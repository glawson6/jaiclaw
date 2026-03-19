package io.jclaw.memory

import io.jclaw.core.model.UserMessage
import io.jclaw.core.model.AssistantMessage
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class SessionTranscriptStoreSpec extends Specification {

    @TempDir
    Path tempDir

    SessionTranscriptStore store

    def setup() {
        store = new SessionTranscriptStore(tempDir)
    }

    def "appends and reads transcript messages"() {
        given:
        def sessionKey = "agent1:shell:local:user"

        when:
        store.appendMessage(sessionKey, new UserMessage("1", "Hello", "user1"))
        store.appendMessage(sessionKey, new AssistantMessage("2", "Hi there!", "model1"))

        then:
        def lines = store.readTranscript(sessionKey)
        lines.size() == 2
        lines[0].contains('"role":"user"')
        lines[0].contains('"content":"Hello"')
        lines[1].contains('"role":"assistant"')
    }

    def "exists returns false for missing session"() {
        expect:
        !store.exists("nonexistent:session")
    }

    def "exists returns true after writing"() {
        given:
        store.appendMessage("test:session", new UserMessage("1", "hi", "u"))

        expect:
        store.exists("test:session")
    }

    def "readTranscript returns empty for missing session"() {
        expect:
        store.readTranscript("missing:session") == []
    }
}
