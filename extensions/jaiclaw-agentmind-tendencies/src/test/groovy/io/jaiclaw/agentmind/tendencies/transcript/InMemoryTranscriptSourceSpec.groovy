package io.jaiclaw.agentmind.tendencies.transcript

import spock.lang.Specification

class InMemoryTranscriptSourceSpec extends Specification {

    InMemoryTranscriptSource src = new InMemoryTranscriptSource(5)

    def "record then recentMessages round-trips per session"() {
        when:
        src.record("session-1", "hello")
        src.record("session-1", "world")
        src.record("session-2", "other")

        then:
        src.recentMessages("session-1") == ["hello", "world"]
        src.recentMessages("session-2") == ["other"]
    }

    def "recentMessages for an unknown session returns empty list"() {
        expect:
        src.recentMessages("ghost") == []
    }

    def "record bounded by maxMessagesPerSession — older messages roll off"() {
        when:
        (1..10).each { i -> src.record("s", "msg" + i) }

        then:
        src.recentMessages("s") == ["msg6", "msg7", "msg8", "msg9", "msg10"]
    }

    def "clear removes the session's transcript"() {
        given:
        src.record("s", "x")

        when:
        src.clear("s")

        then:
        src.recentMessages("s") == []
    }

    def "null sessionKey or content is silently ignored"() {
        when:
        src.record(null, "x")
        src.record("s", null)

        then:
        src.recentMessages("s") == []
        notThrown(Exception)
    }

    def "rejects invalid maxMessagesPerSession"() {
        when:
        new InMemoryTranscriptSource(0)

        then:
        thrown(IllegalArgumentException)
    }
}
