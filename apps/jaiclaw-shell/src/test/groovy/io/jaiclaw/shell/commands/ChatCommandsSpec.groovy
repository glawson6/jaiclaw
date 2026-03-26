package io.jaiclaw.shell.commands

import io.jaiclaw.agent.AgentRuntime
import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.config.AgentProperties
import io.jaiclaw.config.JaiClawProperties
import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.model.UserMessage
import org.springframework.beans.factory.ObjectProvider
import spock.lang.Specification

class ChatCommandsSpec extends Specification {

    AgentRuntime agentRuntime = Mock()
    ObjectProvider<AgentRuntime> agentRuntimeProvider = Mock() {
        getIfAvailable() >> agentRuntime
    }
    SessionManager sessionManager = new SessionManager()
    JaiClawProperties properties = Mock() {
        agent() >> Mock(AgentProperties) {
            defaultAgent() >> "default"
        }
    }

    ChatCommands commands = new ChatCommands(agentRuntimeProvider, sessionManager, properties)

    def "sessions returns no sessions message when empty"() {
        expect:
        commands.sessions() == "No active sessions."
    }

    def "sessions lists active sessions"() {
        given:
        sessionManager.getOrCreate("session-1", "default")
        sessionManager.getOrCreate("session-2", "default")

        when:
        def result = commands.sessions()

        then:
        result.contains("session-1")
        result.contains("session-2")
        result.contains("ACTIVE")
    }

    def "sessions marks current session with asterisk"() {
        given:
        sessionManager.getOrCreate("default", "default")

        when:
        def result = commands.sessions()

        then:
        result.contains(" * ")
    }

    def "session-history returns not found for unknown session"() {
        expect:
        commands.sessionHistory("nonexistent") == "Session not found: nonexistent"
    }

    def "session-history returns no messages for empty session"() {
        given:
        sessionManager.getOrCreate("default", "default")

        when:
        def result = commands.sessionHistory("")

        then:
        result.contains("No messages")
    }

    def "session-history shows messages in session"() {
        given:
        sessionManager.getOrCreate("default", "default")
        sessionManager.appendMessage("default", new UserMessage("m1", "Hello", "user1"))
        sessionManager.appendMessage("default", new AssistantMessage("m2", "Hi there!", "gpt-4o"))

        when:
        def result = commands.sessionHistory("")

        then:
        result.contains("[USER]")
        result.contains("Hello")
        result.contains("[ASSISTANT]")
        result.contains("Hi there!")
    }

    def "session-history uses specified session key"() {
        given:
        sessionManager.getOrCreate("other-session", "default")
        sessionManager.appendMessage("other-session", new UserMessage("m1", "Test message", "user1"))

        when:
        def result = commands.sessionHistory("other-session")

        then:
        result.contains("other-session")
        result.contains("Test message")
    }

    def "new-session resets and creates new session key"() {
        when:
        def result = commands.newSession()

        then:
        result.startsWith("New session started:")
        commands.getCurrentSessionKey() != "default"
    }
}
