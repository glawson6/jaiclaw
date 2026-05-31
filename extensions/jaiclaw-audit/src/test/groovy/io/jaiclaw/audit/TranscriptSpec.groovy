package io.jaiclaw.audit

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class TranscriptSpec extends Specification {

    @TempDir
    Path tempDir

    // --- TranscriptUtterance tests ---

    def "user factory creates user utterance"() {
        when:
        TranscriptUtterance utterance = TranscriptUtterance.user("Hello")

        then:
        utterance.role() == "user"
        utterance.content() == "Hello"
        utterance.timestamp() != null
        utterance.metadata() == Map.of()
    }

    def "assistant factory creates assistant utterance"() {
        when:
        TranscriptUtterance utterance = TranscriptUtterance.assistant("Hi there")

        then:
        utterance.role() == "assistant"
        utterance.content() == "Hi there"
    }

    def "tool factory includes tool name in metadata"() {
        when:
        TranscriptUtterance utterance = TranscriptUtterance.tool("web_search", "5 results")

        then:
        utterance.role() == "tool"
        utterance.content() == "5 results"
        utterance.metadata().get("toolName") == "web_search"
    }

    def "utterance defaults handle nulls"() {
        when:
        TranscriptUtterance utterance = new TranscriptUtterance(null, null, null, null)

        then:
        utterance.role() == "unknown"
        utterance.content() == ""
        utterance.timestamp() != null
        utterance.metadata() == Map.of()
    }

    // --- TranscriptSession tests ---

    def "session requires sessionId"() {
        when:
        new TranscriptSession(null, null, null, null, null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "session rejects blank sessionId"() {
        when:
        new TranscriptSession("", null, null, null, null, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "session defaults handle nulls"() {
        when:
        TranscriptSession session = new TranscriptSession("sess-1", null, null, null, null, null)

        then:
        session.sessionId() == "sess-1"
        session.startTime() != null
        session.utterances() == []
    }

    def "withUtterance appends to session"() {
        given:
        TranscriptSession session = TranscriptSession.builder()
                .sessionId("sess-1")
                .build()

        when:
        TranscriptSession updated = session.withUtterance(TranscriptUtterance.user("Hello"))

        then:
        updated.utterances().size() == 1
        updated.utterances()[0].role() == "user"
        // Original is unchanged
        session.utterances().isEmpty()
    }

    // --- FileTranscriptStore tests ---

    def "save and load round-trip"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)
        TranscriptSession session = TranscriptSession.builder()
                .sessionId("sess-1")
                .tenantId("tenant-a")
                .agentId("agent-1")
                .channel("telegram")
                .startTime(Instant.parse("2026-05-29T10:00:00Z"))
                .utterances([
                    TranscriptUtterance.user("Hello"),
                    TranscriptUtterance.assistant("Hi there!")
                ])
                .build()

        when:
        store.save(session)
        Optional<TranscriptSession> loaded = store.load("sess-1")

        then:
        loaded.isPresent()
        loaded.get().sessionId() == "sess-1"
        loaded.get().tenantId() == "tenant-a"
        loaded.get().agentId() == "agent-1"
        loaded.get().channel() == "telegram"
        loaded.get().utterances().size() == 2
    }

    def "save creates tenant and date directories"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)
        TranscriptSession session = TranscriptSession.builder()
                .sessionId("sess-1")
                .tenantId("tenant-a")
                .startTime(Instant.parse("2026-05-29T10:00:00Z"))
                .build()

        when:
        store.save(session)

        then:
        Files.isDirectory(tempDir.resolve("tenant-a/2026-05-29"))
        Files.exists(tempDir.resolve("tenant-a/2026-05-29/sess-1.json"))
    }

    def "save uses _default for null tenant"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)
        TranscriptSession session = TranscriptSession.builder()
                .sessionId("sess-1")
                .startTime(Instant.parse("2026-05-29T10:00:00Z"))
                .build()

        when:
        store.save(session)

        then:
        Files.exists(tempDir.resolve("_default/2026-05-29/sess-1.json"))
    }

    def "load returns empty for nonexistent session"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)

        expect:
        store.load("nonexistent").isEmpty()
    }

    def "list returns session IDs for tenant"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)
        store.save(TranscriptSession.builder()
                .sessionId("sess-1")
                .tenantId("t1")
                .startTime(Instant.parse("2026-05-29T10:00:00Z"))
                .build())
        store.save(TranscriptSession.builder()
                .sessionId("sess-2")
                .tenantId("t1")
                .startTime(Instant.parse("2026-05-29T11:00:00Z"))
                .build())

        when:
        List<String> sessions = store.list("t1", 10)

        then:
        sessions.size() == 2
        sessions.containsAll(["sess-1", "sess-2"])
    }

    def "list respects limit"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)
        store.save(TranscriptSession.builder().sessionId("s1").tenantId("t1").build())
        store.save(TranscriptSession.builder().sessionId("s2").tenantId("t1").build())
        store.save(TranscriptSession.builder().sessionId("s3").tenantId("t1").build())

        when:
        List<String> sessions = store.list("t1", 2)

        then:
        sessions.size() == 2
    }

    def "list returns empty for unknown tenant"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)

        expect:
        store.list("nonexistent", 10).isEmpty()
    }

    def "delete removes transcript file"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)
        store.save(TranscriptSession.builder()
                .sessionId("sess-1")
                .tenantId("t1")
                .startTime(Instant.parse("2026-05-29T10:00:00Z"))
                .build())

        when:
        boolean deleted = store.delete("sess-1")

        then:
        deleted
        store.load("sess-1").isEmpty()
    }

    def "delete returns false for nonexistent session"() {
        given:
        FileTranscriptStore store = new FileTranscriptStore(tempDir)

        expect:
        !store.delete("nonexistent")
    }

    // --- TranscriptSummaryRenderer tests ---

    def "render produces markdown with session header"() {
        given:
        TranscriptSummaryRenderer renderer = new TranscriptSummaryRenderer()
        TranscriptSession session = TranscriptSession.builder()
                .sessionId("sess-1")
                .agentId("agent-1")
                .channel("telegram")
                .startTime(Instant.parse("2026-05-29T10:00:00Z"))
                .utterances([
                    TranscriptUtterance.user("Hello"),
                    TranscriptUtterance.assistant("Hi there!")
                ])
                .build()

        when:
        String markdown = renderer.render(session)

        then:
        markdown.contains("# Transcript: sess-1")
        markdown.contains("**Agent:** agent-1")
        markdown.contains("**Channel:** telegram")
        markdown.contains("**Messages:** 2")
        markdown.contains("### User")
        markdown.contains("### Assistant")
        markdown.contains("Hello")
        markdown.contains("Hi there!")
    }

    def "render includes tool name for tool utterances"() {
        given:
        TranscriptSummaryRenderer renderer = new TranscriptSummaryRenderer()
        TranscriptSession session = TranscriptSession.builder()
                .sessionId("sess-1")
                .utterances([TranscriptUtterance.tool("web_search", "results")])
                .build()

        when:
        String markdown = renderer.render(session)

        then:
        markdown.contains("### Tool Result (web_search)")
    }

    def "render truncates long content"() {
        given:
        TranscriptSummaryRenderer renderer = new TranscriptSummaryRenderer()
        String longContent = "x" * 2000
        TranscriptSession session = TranscriptSession.builder()
                .sessionId("sess-1")
                .utterances([TranscriptUtterance.user(longContent)])
                .build()

        when:
        String markdown = renderer.render(session)

        then:
        markdown.contains("(truncated)")
    }
}
