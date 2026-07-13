package io.jaiclaw.embabel.delegate

import com.embabel.agent.core.Agent
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcess
import com.embabel.agent.core.AgentProcessStatusCode
import com.embabel.agent.core.Blackboard
import com.embabel.agent.core.ProcessOptions
import tools.jackson.databind.ObjectMapper
import io.jaiclaw.agent.delegate.AgentLoopDelegateContext
import io.jaiclaw.config.AgentLoopDelegateConfig
import io.jaiclaw.config.TenantAgentConfig
import spock.lang.Specification
import spock.lang.Subject

class EmbabelAgentLoopDelegateSpec extends Specification {

    AgentPlatform agentPlatform = Mock()
    ObjectMapper objectMapper = new ObjectMapper()

    @Subject
    EmbabelAgentLoopDelegate delegate = new EmbabelAgentLoopDelegate(agentPlatform, objectMapper)

    def "delegateId returns 'embabel'"() {
        expect:
        delegate.delegateId() == "embabel"
    }

    def "canHandle returns true when delegate config matches"() {
        given:
        AgentLoopDelegateConfig delegateConfig = AgentLoopDelegateConfig.builder()
                .enabled(true)
                .delegateId("embabel")
                .workflow("TestAgent")
                .build()
        TenantAgentConfig tenantConfig = TenantAgentConfig.builder()
                .tenantId("t1")
                .agentId("default")
                .loopDelegate(delegateConfig)
                .build()

        expect:
        delegate.canHandle(tenantConfig)
    }

    def "canHandle returns false when delegate is disabled"() {
        given:
        AgentLoopDelegateConfig delegateConfig = AgentLoopDelegateConfig.builder()
                .enabled(false)
                .delegateId("embabel")
                .build()
        TenantAgentConfig tenantConfig = TenantAgentConfig.builder()
                .tenantId("t1")
                .agentId("default")
                .loopDelegate(delegateConfig)
                .build()

        expect:
        !delegate.canHandle(tenantConfig)
    }

    def "canHandle returns false when delegate ID does not match"() {
        given:
        AgentLoopDelegateConfig delegateConfig = AgentLoopDelegateConfig.builder()
                .enabled(true)
                .delegateId("langchain4j")
                .build()
        TenantAgentConfig tenantConfig = TenantAgentConfig.builder()
                .tenantId("t1")
                .agentId("default")
                .loopDelegate(delegateConfig)
                .build()

        expect:
        !delegate.canHandle(tenantConfig)
    }

    def "canHandle returns false when loop delegate is null"() {
        given:
        TenantAgentConfig tenantConfig = TenantAgentConfig.builder()
                .tenantId("t1")
                .agentId("default")
                .build()

        expect:
        !delegate.canHandle(tenantConfig)
    }

    def "execute returns success with serialized JSON when agent completes"() {
        given:
        Agent agent = makeAgent("TestAgent")
        AgentProcess process = Mock()
        Blackboard blackboard = Mock()

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "hello"]) >> process
        process.getStatus() >> AgentProcessStatusCode.COMPLETED
        process.getBlackboard() >> blackboard
        blackboard.lastResult() >> [summary: "A test summary", topics: ["java", "spring"]]

        AgentLoopDelegateContext ctx = buildContext("TestAgent")

        when:
        def result = delegate.execute("hello", ctx)

        then:
        result.success()
        result.content().contains("A test summary")
        result.content().contains("java")
    }

    def "execute returns success with plain string when result is a String"() {
        given:
        Agent agent = makeAgent("TextAgent")
        AgentProcess process = Mock()
        Blackboard blackboard = Mock()

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "input"]) >> process
        process.getStatus() >> AgentProcessStatusCode.COMPLETED
        process.getBlackboard() >> blackboard
        blackboard.lastResult() >> "Plain text result"

        AgentLoopDelegateContext ctx = buildContext("TextAgent")

        when:
        def result = delegate.execute("input", ctx)

        then:
        result.success()
        result.content() == "Plain text result"
    }

    def "execute returns failure when agent does not complete"() {
        given:
        Agent agent = makeAgent("FailAgent")
        AgentProcess process = Mock()

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "input"]) >> process
        process.getStatus() >> AgentProcessStatusCode.FAILED
        process.getFailureInfo() >> "NullPointerException"

        AgentLoopDelegateContext ctx = buildContext("FailAgent")

        when:
        def result = delegate.execute("input", ctx)

        then:
        !result.success()
        result.content().contains("NullPointerException")
    }

    def "execute returns failure when blackboard has no last result"() {
        given:
        Agent agent = makeAgent("EmptyAgent")
        AgentProcess process = Mock()
        Blackboard blackboard = Mock()

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "input"]) >> process
        process.getStatus() >> AgentProcessStatusCode.COMPLETED
        process.getBlackboard() >> blackboard
        blackboard.lastResult() >> null

        AgentLoopDelegateContext ctx = buildContext("EmptyAgent")

        when:
        def result = delegate.execute("input", ctx)

        then:
        !result.success()
        result.content().contains("no result")
    }

    def "execute throws when agent name not found"() {
        given:
        Agent agent = makeAgent("OtherAgent")
        agentPlatform.agents() >> [agent]

        AgentLoopDelegateContext ctx = buildContext("MissingAgent")

        when:
        delegate.execute("input", ctx)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("MissingAgent")
        e.message.contains("OtherAgent")
    }

    def "execute returns failure when runtime exception occurs"() {
        given:
        Agent agent = makeAgent("CrashAgent")
        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "input"]) >> {
            throw new RuntimeException("Boom")
        }

        AgentLoopDelegateContext ctx = buildContext("CrashAgent")

        when:
        def result = delegate.execute("input", ctx)

        then:
        !result.success()
        result.content().contains("Boom")
    }

    private static Agent makeAgent(String name) {
        return new Agent(name, "test", "1.0.0", "Test agent: " + name,
                Collections.emptySet() as Set, Collections.emptyList() as List)
    }

    private AgentLoopDelegateContext buildContext(String workflow) {
        AgentLoopDelegateConfig delegateConfig = AgentLoopDelegateConfig.builder()
                .enabled(true)
                .delegateId("embabel")
                .workflow(workflow)
                .build()
        TenantAgentConfig tenantConfig = TenantAgentConfig.builder()
                .tenantId("t1")
                .agentId("default")
                .loopDelegate(delegateConfig)
                .build()
        return AgentLoopDelegateContext.builder()
                .sessionKey("default:test:acc:peer")
                .tenantId("t1")
                .tenantConfig(tenantConfig)
                .systemPrompt("You are a test agent")
                .build()
    }
}
