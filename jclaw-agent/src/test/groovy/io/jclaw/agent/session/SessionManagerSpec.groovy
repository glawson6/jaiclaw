package io.jclaw.agent.session

import io.jclaw.core.model.SessionState
import io.jclaw.core.model.UserMessage
import io.jclaw.core.model.AssistantMessage
import spock.lang.Specification

class SessionManagerSpec extends Specification {

    SessionManager manager = new SessionManager()

    def "getOrCreate creates new session"() {
        when:
        def session = manager.getOrCreate("key1", "agent1")

        then:
        session != null
        session.sessionKey() == "key1"
        session.agentId() == "agent1"
        session.state() == SessionState.ACTIVE
        session.messages().isEmpty()
    }

    def "getOrCreate returns existing session on second call"() {
        given:
        def first = manager.getOrCreate("key1", "agent1")

        when:
        def second = manager.getOrCreate("key1", "agent1")

        then:
        first.id() == second.id()
    }

    def "appendMessage adds message to session"() {
        given:
        manager.getOrCreate("key1", "agent1")
        def msg = new UserMessage("m1", "hello", "user")

        when:
        manager.appendMessage("key1", msg)

        then:
        def session = manager.get("key1").get()
        session.messages().size() == 1
        session.messages()[0].content() == "hello"
    }

    def "appendMessage preserves message order"() {
        given:
        manager.getOrCreate("key1", "agent1")

        when:
        manager.appendMessage("key1", new UserMessage("m1", "first", "user"))
        manager.appendMessage("key1", new AssistantMessage("m2", "second", "model"))
        manager.appendMessage("key1", new UserMessage("m3", "third", "user"))

        then:
        def messages = manager.get("key1").get().messages()
        messages.size() == 3
        messages[0].content() == "first"
        messages[1].content() == "second"
        messages[2].content() == "third"
    }

    def "get returns empty for unknown session"() {
        expect:
        manager.get("nope").isEmpty()
    }

    def "transitionState changes session state"() {
        given:
        manager.getOrCreate("key1", "agent1")

        when:
        manager.transitionState("key1", SessionState.IDLE)

        then:
        manager.get("key1").get().state() == SessionState.IDLE
    }

    def "close sets session to CLOSED"() {
        given:
        manager.getOrCreate("key1", "agent1")

        when:
        manager.close("key1")

        then:
        manager.get("key1").get().state() == SessionState.CLOSED
    }

    def "reset removes session"() {
        given:
        manager.getOrCreate("key1", "agent1")

        when:
        manager.reset("key1")

        then:
        manager.get("key1").isEmpty()
        !manager.exists("key1")
    }

    def "listSessions returns all sessions"() {
        given:
        manager.getOrCreate("k1", "a1")
        manager.getOrCreate("k2", "a1")
        manager.getOrCreate("k3", "a2")

        expect:
        manager.listSessions().size() == 3
    }

    def "listActiveSessions excludes closed sessions"() {
        given:
        manager.getOrCreate("k1", "a1")
        manager.getOrCreate("k2", "a1")
        manager.close("k2")

        when:
        def active = manager.listActiveSessions()

        then:
        active.size() == 1
        active[0].sessionKey() == "k1"
    }

    def "messageCount returns number of messages"() {
        given:
        manager.getOrCreate("key1", "agent1")
        manager.appendMessage("key1", new UserMessage("m1", "hello", "user"))
        manager.appendMessage("key1", new AssistantMessage("m2", "hi", "model"))

        expect:
        manager.messageCount("key1") == 2
    }

    def "messageCount returns 0 for unknown session"() {
        expect:
        manager.messageCount("ghost") == 0
    }

    def "sessionCount tracks total sessions"() {
        given:
        manager.getOrCreate("k1", "a1")
        manager.getOrCreate("k2", "a1")

        expect:
        manager.sessionCount() == 2

        when:
        manager.reset("k1")

        then:
        manager.sessionCount() == 1
    }

    def "exists returns correct state"() {
        expect:
        !manager.exists("key1")

        when:
        manager.getOrCreate("key1", "agent1")

        then:
        manager.exists("key1")
    }
}
