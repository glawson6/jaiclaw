package io.jaiclaw.kanban.e2e.surfaces

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.mcp.KanbanMcpToolProvider
import io.jaiclaw.kanban.persistence.BoardStore
import io.jaiclaw.kanban.service.TransitionHistory
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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
 * Phase 2 end-to-end coverage per implementation plan §5.2. Boots the full
 * kanban auto-config stack on a real RANDOM_PORT Tomcat against the shared
 * fixture board (e2e-content-review.yaml from §5.1) and drives every Phase 2
 * surface together: REST + SSE + ASCII + MCP + Actuator + the dynamic-card
 * scenario for analysis §9 Q1.
 *
 * <p>The MCP surface here is invoked via the {@link KanbanMcpToolProvider}
 * SPI directly — not over HTTP — because HTTP MCP hosting lives in
 * {@code jaiclaw-gateway}, which would drag a much larger context into a
 * kanban-extension spec. The SPI is what the gateway calls into, so this
 * still verifies the surface end-to-end at the boundary that matters.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [KanbanSurfacesE2ESpec.TestApp])
@TestPropertySource(properties = [
        "jaiclaw.kanban.enabled=true",
        "jaiclaw.kanban.locations.patterns=classpath:boards/e2e-content-review.yaml",
        "jaiclaw.kanban.sse.heartbeat-seconds=1",
        "management.endpoints.web.exposure.include=kanban,health",
])
class KanbanSurfacesE2ESpec extends Specification {

    static final Path tempDir = Files.createTempDirectory("kanban-surfaces-e2e")

    @DynamicPropertySource
    static void registerStorageDirs(DynamicPropertyRegistry registry) {
        registry.add("jaiclaw.kanban.boards-dir", { tempDir.resolve("boards").toString() })
    }

    @LocalServerPort
    int port

    @Autowired
    TestRestTemplate http

    @Autowired
    KanbanMcpToolProvider mcpProvider

    @Autowired
    TaskStore taskStore

    @Autowired
    TransitionHistory transitionHistory

    @Autowired
    BoardStore boardStore

    final HttpClient sseClient = HttpClient.newHttpClient()

    def setup() {
        // Wipe per-test state but keep the bootstrap-loaded fixture board.
        taskStore.findAll().each { taskStore.deleteById(it.id()) }
        transitionHistory.clear("e2e-content-review")
    }

    private String url(String path) { "http://localhost:${port}${path}" }

    def "boots with the fixture board pre-registered through the YAML classpath loader"() {
        when:
        def list = http.getForEntity(url("/api/kanban/boards"), List)

        then:
        list.statusCode == HttpStatus.OK
        list.body*.id.contains("e2e-content-review")
    }

    def "full surfaces e2e: dynamic card, SSE event, ASCII, MCP, actuator"() {
        given: "an SSE stream open on the fixture board"
        def stream = openStream("e2e-content-review")
        def snapshotData = stream.waitForEvent("snapshot")

        expect: "the SSE initial event carries the right board"
        snapshotData != null
        snapshotData.contains("e2e-content-review")

        when: "a new card is added via POST /tasks (the analysis §9 Q1 dynamic-card path)"
        def card = http.postForEntity(
                url("/api/kanban/boards/e2e-content-review/tasks"),
                [name: "End-to-end card", description: "spec-driven"],
                Map).body

        then: "the SSE stream delivers the CREATE transition event"
        def createEvent = stream.waitForStateChangedMatching('"event":"CREATE"')
        createEvent != null
        createEvent.contains("End-to-end card")

        when: "the card is started"
        def transition = http.postForEntity(
                url("/api/kanban/tasks/${card.id}/transition"),
                [event: "START", actor: "e2e"], Map)

        then:
        transition.statusCode == HttpStatus.OK

        and: "the SSE stream delivers the START transition"
        def startEvent = stream.waitForStateChangedMatching('"event":"START"')
        startEvent != null
        startEvent.contains('"toState":"drafting"')

        when: "GET /ascii renders the board including the new card"
        def ascii = http.getForEntity(
                url("/api/kanban/boards/e2e-content-review/ascii?width=100&style=compact"),
                String)

        then:
        ascii.statusCode == HttpStatus.OK
        ascii.headers.getFirst("Content-Type").startsWith(MediaType.TEXT_PLAIN_VALUE)
        ascii.body.contains("End-to-end card")
        ascii.body.contains("E2E Content Review Board")

        when: "MCP tools/list is invoked via the provider SPI (same surface the gateway calls)"
        def tools = mcpProvider.getTools()

        then:
        tools.size() == 5
        tools*.name() as Set == [
                "board_list", "board_show", "board_ascii", "task_move", "task_claim"] as Set

        when: "MCP tools/call task_move advances the same card"
        def mcpMove = mcpProvider.execute("task_move",
                [taskId: card.id, event: "SUBMIT", actor: "mcp-e2e"], null)

        then:
        !mcpMove.isError()
        mcpMove.content().contains('"toState":"review"')

        when: "the SSE stream also sees the MCP-driven SUBMIT event"
        def submitEvent = stream.waitForStateChangedMatching('"event":"SUBMIT"')

        then:
        submitEvent != null

        when: "GET /actuator/kanban exposes the board to operators"
        def actList = http.getForEntity(url("/actuator/kanban"), Map)
        def actById = http.getForEntity(url("/actuator/kanban/e2e-content-review"), Map)

        then:
        actList.statusCode == HttpStatus.OK
        actList.body.engine == "graph"
        (actList.body.boards as List).any { (it as Map).id == "e2e-content-review" }
        actById.statusCode == HttpStatus.OK
        (actById.body.recentTransitions as List).any { (it as Map).event == "START" }

        cleanup:
        stream?.close()
    }

    private SseStream openStream(String boardId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/api/kanban/boards/${boardId}/events")))
                .header("Accept", "text/event-stream")
                .GET()
                .build()
        HttpResponse<InputStream> response = sseClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream())
        return new SseStream(response)
    }

    /** SSE reader (copied shape from KanbanEventControllerSpec). */
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
                } catch (IOException ignored) {}
            }, "sse-e2e-reader")
            reader.daemon = true
            reader.start()
        }

        String waitForEvent(String name) {
            def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            return waitForEventBefore(name, deadline)
        }

        String waitForStateChangedMatching(String substring) {
            def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
            while (System.nanoTime() < deadline) {
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

        void close() {
            closed = true
            try { response.body().close() } catch (IOException ignored) {}
            reader.interrupt()
        }
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        TaskStore surfacesE2ESpecTaskStore() {
            Path dir = Files.createTempDirectory("kanban-surfaces-task-store")
            new JsonFileTaskStore(dir, new TenantGuard(TenantProperties.DEFAULT))
        }
    }
}
