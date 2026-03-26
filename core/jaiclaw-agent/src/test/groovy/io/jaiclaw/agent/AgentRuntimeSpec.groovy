package io.jaiclaw.agent

import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.core.agent.*
import io.jaiclaw.core.hook.HookName
import io.jaiclaw.core.model.AgentIdentity
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.model.UserMessage
import io.jaiclaw.core.skill.SkillDefinition
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.tools.ToolRegistry
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort
import org.springframework.ai.chat.client.ChatClient
import spock.lang.Specification

class AgentRuntimeSpec extends Specification {

    SessionManager sessionManager = new SessionManager()
    ChatClient.Builder chatClientBuilder = Mock()
    ToolRegistry toolRegistry = new ToolRegistry()
    List<SkillDefinition> skills = []

    def setup() {
        sessionManager.getOrCreate("test-session", "default")
    }

    def "backward-compatible 4-arg constructor works"() {
        when:
        def runtime = new AgentRuntime(sessionManager, chatClientBuilder, toolRegistry, skills)

        then:
        runtime != null
    }

    def "full constructor accepts all SPI collaborators"() {
        given:
        def hooks = Mock(AgentHookDispatcher)
        def compactor = Mock(ContextCompactor)
        def memory = Mock(MemoryProvider)
        def approval = Mock(ToolApprovalHandler)
        def orchestration = Mock(AgentOrchestrationPort)

        when:
        def runtime = new AgentRuntime(
                sessionManager, chatClientBuilder, toolRegistry, skills,
                null, ToolLoopConfig.DEFAULT, compactor, hooks, memory, approval, orchestration
        )

        then:
        runtime != null
    }

    def "full constructor accepts all null SPIs"() {
        when:
        def runtime = new AgentRuntime(
                sessionManager, chatClientBuilder, toolRegistry, skills,
                null, null, null, null, null, null, null
        )

        then:
        runtime != null
    }

    def "cancel removes from active tasks"() {
        given:
        def runtime = new AgentRuntime(sessionManager, chatClientBuilder, toolRegistry, skills)

        when:
        runtime.cancel("test-session")

        then:
        !runtime.isRunning("test-session")
    }

    def "isRunning returns false for unknown session"() {
        given:
        def runtime = new AgentRuntime(sessionManager, chatClientBuilder, toolRegistry, skills)

        expect:
        !runtime.isRunning("unknown-session")
    }
}
