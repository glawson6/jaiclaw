package io.jaiclaw.kanban.web.events

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.model.BoardDefinition
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.TerminalKind
import io.jaiclaw.kanban.model.TransitionDefinition
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskStatus
import io.jaiclaw.tasks.TaskStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Full-stack integration for {@link io.jaiclaw.kanban.web.KanbanEventController}.
 * Boots a real RANDOM_PORT Tomcat, opens a raw SSE connection with
 * {@code java.net.http.HttpClient}, drives transitions through the REST
 * controller, and asserts the events flow on the stream.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [KanbanEventControllerSpec.TestApp])
@TestPropertySource(properties = [
        "jaiclaw.kanban.enabled=true",
        // Heartbeat fast enough to verify, slow enough to not dominate logs.
        "jaiclaw.kanban.sse.heartbeat-seconds=1",
])
class KanbanEventControllerSpec extends Specification {

    static final Path tempDir = Files.createTempDirectory("kanban-sse-spec")

    @DynamicPropertySource
    static void registerStorageDirs(DynamicPropertyRegistry registry) {
        registry.add("jaiclaw.kanban.boards-dir", { tempDir.resolve("boards").toString() })
    }

    @LocalServerPort
    int port

    @Autowired
    TestRestTemplate http

    @Autowired
    io.jaiclaw.kanban.service.KanbanBoardService boardService

    @Autowired
    TaskStore taskStore

    @Autowired
    io.jaiclaw.kanban.service.TransitionHistory transitionHistory

    final HttpClient client = HttpClient.newHttpClient()

    def setup() {
        boardService.listAllUnscoped().each { boardService.remove(it.id()) }
        taskStore.findAll().each { taskStore.deleteById(it.id()) }
        transitionHistory.clear("sse")
        // Register the fixture board.
        def board = new BoardDefinition("sse", "SSE Board", [], "backlog", [
                new ColumnDefinition("backlog",  "Backlog", TaskStatus.QUEUED,  null, false, null, null),
                new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING, null, false, null, null),
                new ColumnDefinition("done",     "Done", TaskStatus.SUCCEEDED, null, true, TerminalKind.SUCCESS, null),
        ], [
                new TransitionDefinition("backlog",  "drafting", "START",  [:]),
                new TransitionDefinition("drafting", "done",     "FINISH", [:]),
        ])
        boardService.register(board)
    }

    private String url(String path) { "http://localhost:${port}${path}" }

    /**
     * Open an SSE connection and stream lines onto a queue until {@code close()}
     * is called. {@code take()} blocks until the next line is available.
     */
    private SseStream openStream(String boardId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/kanban/boards/${boardId}/events")))
                .header("Accept", "text/event-stream")
                .GET()
                .build()
        HttpResponse<InputStream> response = client.send(request,
                HttpResponse.BodyHandlers.ofInputStream())
        return new SseStream(response)
    }

    def "GET /events for unknown board returns 404"() {
        when:
        def response = http.getForEntity(url("/api/kanban/boards/missing/events"), Map)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
    }

    def "GET /events sends an initial snapshot and then state-changed events"() {
        given:
        def stream = openStream("sse")

        when: "drain until the first data: line — that's the snapshot"
        def snapshot = stream.waitForEvent("snapshot")

        then:
        snapshot != null
        snapshot.contains("sse")

        when: "a card is created and transitioned"
        def card = http.postForEntity(url("/api/kanban/boards/sse/tasks"),
                [name: "SSE Card"], Map).body
        http.postForEntity(url("/api/kanban/tasks/${card.id}/transition"),
                [event: "START", actor: "alice"], Map)

        and: "drain CREATE (fired by createCard) and the subsequent START"
        def startEvent = stream.waitForStateChangedMatching('"event":"START"')

        then:
        startEvent != null
        startEvent.contains('"event":"START"')
        startEvent.contains('"toState":"drafting"')

        cleanup:
        stream?.close()
    }

    def "heartbeat keeps the stream alive (no early close)"() {
        given:
        def stream = openStream("sse")

        // Wait for at least one heartbeat tick (heartbeat=1s). Heartbeats are
        // raw ': hb' comment lines per the SSE spec.
        when:
        def heartbeat = stream.waitFor({ it.startsWith(":") }, 5)

        then:
        heartbeat != null

        cleanup:
        stream?.close()
    }

    /** Small SSE reader: parses framed events on a background thread. */
    private static class SseStream {
        private final HttpResponse<InputStream> response
        private final LinkedBlockingQueue<String> rawLines = new LinkedBlockingQueue<>()
        private final Thread reader
        private volatile boolean closed = false

        SseStream(HttpResponse<InputStream> response) {
            this.response = response
            this.reader = new Thread({
                try (def br = new BufferedReader(new InputStreamReader(
                        response.body(), StandardCharsets.UTF_8))) {
                    String line
                    while (!closed && (line = br.readLine()) != null) {
                        rawLines.put(line)
                    }
                } catch (IOException ignored) {
                    // Stream closed by client — expected at end of test.
                }
            }, "sse-test-reader")
            reader.daemon = true
            reader.start()
        }

        /**
         * Block until a non-comment line matching the named event arrives. SSE
         * framing: {@code event: <name>\ndata: <json>\n\n}.
         */
        String waitForEvent(String name) {
            def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            String pendingEvent = null
            while (System.nanoTime() < deadline) {
                String line = rawLines.poll(500, TimeUnit.MILLISECONDS)
                if (line == null) continue
                if (line.startsWith("event:")) {
                    pendingEvent = line.substring("event:".length()).trim()
                    continue
                }
                if (line.startsWith("data:") && name.equals(pendingEvent)) {
                    return line.substring("data:".length()).trim()
                }
            }
            return null
        }

        /**
         * Repeated {@link #waitForEvent(String)} for {@code state-changed},
         * returning the first one whose data payload contains the substring.
         */
        String waitForStateChangedMatching(String substring) {
            def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                long remainMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remainMs <= 0) return null
                String data = waitForEventBefore("state-changed", deadline)
                if (data == null) return null
                if (data.contains(substring)) return data
            }
            return null
        }

        private String waitForEventBefore(String name, long deadline) {
            String pendingEvent = null
            while (System.nanoTime() < deadline) {
                long remainMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remainMs <= 0) return null
                String line = rawLines.poll(Math.min(remainMs, 500L), TimeUnit.MILLISECONDS)
                if (line == null) continue
                if (line.startsWith("event:")) {
                    pendingEvent = line.substring("event:".length()).trim()
                    continue
                }
                if (line.startsWith("data:") && name.equals(pendingEvent)) {
                    return line.substring("data:".length()).trim()
                }
            }
            return null
        }

        /** Block until any line matching the predicate arrives. */
        String waitFor(Closure<Boolean> predicate, int seconds) {
            def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
            while (System.nanoTime() < deadline) {
                String line = rawLines.poll(200, TimeUnit.MILLISECONDS)
                if (line == null) continue
                if (predicate.call(line)) return line
            }
            return null
        }

        void close() {
            closed = true
            try { response.body().close() } catch (IOException ignored) {}
            reader.interrupt()
        }
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        TaskStore sseSpecTaskStore() {
            Path dir = Files.createTempDirectory("kanban-sse-task-store")
            new JsonFileTaskStore(dir, new TenantGuard(TenantProperties.DEFAULT))
        }
    }
}
