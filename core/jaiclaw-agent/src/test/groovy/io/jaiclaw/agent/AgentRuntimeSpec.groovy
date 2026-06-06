package io.jaiclaw.agent

import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.config.AgentProperties
import io.jaiclaw.config.CompositeToolProfileRegistry
import io.jaiclaw.core.agent.*
import io.jaiclaw.core.hook.HookName
import io.jaiclaw.core.model.AgentIdentity
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.model.UserMessage
import io.jaiclaw.core.skill.SkillDefinition
import io.jaiclaw.core.tool.CompositeToolProfile
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

    def "AgentRuntimeContext stateless defaults to false"() {
        given:
        def session = Session.create("s1", "key", "agent")
        def ctx = new AgentRuntimeContext("agent", "key", session)

        expect:
        !ctx.stateless()
    }

    def "AgentRuntimeContext stateless can be set via 8-arg constructor"() {
        given:
        def session = Session.create("s1", "key", "agent")
        def ctx = new AgentRuntimeContext("agent", "key", session,
                AgentIdentity.DEFAULT, ToolProfile.FULL, ".", null, true)

        expect:
        ctx.stateless()
    }

    def "AgentRuntimeContext builder supports stateless"() {
        given:
        def session = Session.create("s1", "key", "agent")
        def ctx = AgentRuntimeContext.builder()
                .agentId("agent")
                .sessionKey("key")
                .session(session)
                .stateless(true)
                .build()

        expect:
        ctx.stateless()
    }

    def "full constructor accepts ToolPolicyConfig"() {
        given:
        def policy = new AgentProperties.ToolPolicyConfig("minimal", [], ["terminal"])

        when:
        def runtime = new AgentRuntime(
                sessionManager, chatClientBuilder, toolRegistry, skills,
                null, null, null, null, null, null, null,
                null, null, null, null, false, policy
        )

        then:
        runtime != null
    }

    def "full constructor handles null ToolPolicyConfig gracefully"() {
        when:
        def runtime = new AgentRuntime(
                sessionManager, chatClientBuilder, toolRegistry, skills,
                null, null, null, null, null, null, null,
                null, null, null, null, false, null
        )

        then:
        runtime != null
    }

    def "builder supports defaultToolPolicy"() {
        given:
        def policy = new AgentProperties.ToolPolicyConfig("coding", ["grep", "glob"], [])

        when:
        def runtime = AgentRuntime.builder()
                .sessionManager(sessionManager)
                .chatClientBuilder(chatClientBuilder)
                .toolRegistry(toolRegistry)
                .skills(skills)
                .defaultToolPolicy(policy)
                .build()

        then:
        runtime != null
    }

    def "builder without defaultToolPolicy uses default"() {
        when:
        def runtime = AgentRuntime.builder()
                .sessionManager(sessionManager)
                .chatClientBuilder(chatClientBuilder)
                .toolRegistry(toolRegistry)
                .skills(skills)
                .build()

        then:
        runtime != null
    }

    def "constructor accepts CompositeToolProfileRegistry"() {
        given:
        def registry = new CompositeToolProfileRegistry()
        registry.register(CompositeToolProfile.builder("devops")
                .profiles(ToolProfile.CODING, ToolProfile.MESSAGING)
                .build())

        when:
        def runtime = new AgentRuntime(
                sessionManager, chatClientBuilder, toolRegistry, skills,
                null, null, null, null, null, null, null,
                null, null, null, null, false, null, registry
        )

        then:
        runtime != null
    }

    def "builder supports compositeProfileRegistry"() {
        given:
        def registry = new CompositeToolProfileRegistry()

        when:
        def runtime = AgentRuntime.builder()
                .sessionManager(sessionManager)
                .chatClientBuilder(chatClientBuilder)
                .toolRegistry(toolRegistry)
                .skills(skills)
                .compositeProfileRegistry(registry)
                .build()

        then:
        runtime != null
    }

    def "null compositeProfileRegistry is backward compatible"() {
        when:
        def runtime = new AgentRuntime(
                sessionManager, chatClientBuilder, toolRegistry, skills,
                null, null, null, null, null, null, null,
                null, null, null, null, false, null, null
        )

        then:
        runtime != null
    }
}
