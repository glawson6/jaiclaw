package io.jaiclaw.embabel.delegate

import com.embabel.agent.core.Agent
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcess
import com.embabel.agent.core.AgentProcessStatusCode
import com.embabel.agent.core.Blackboard
import com.embabel.agent.core.ProcessOptions
import com.fasterxml.jackson.databind.ObjectMapper
import io.jaiclaw.tools.bridge.embabel.OrchestrationResult
import io.jaiclaw.tools.bridge.embabel.WorkflowDescriptor
import spock.lang.Specification
import spock.lang.Subject

/**
 * Coverage for the pipeline-side Embabel orchestration port. Mirrors
 * the lookup + status + serialization scenarios from
 * {@link EmbabelAgentLoopDelegateSpec} but through the
 * {@link io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort} surface.
 */
class EmbabelAgentOrchestrationPortSpec extends Specification {

    AgentPlatform agentPlatform = Mock()
    ObjectMapper objectMapper = new ObjectMapper()

    @Subject
    EmbabelAgentOrchestrationPort port = new EmbabelAgentOrchestrationPort(agentPlatform, objectMapper)

    def "platformName returns 'embabel'"() {
        expect:
        port.platformName() == "embabel"
    }

    def "isAvailable returns true when AgentPlatform is wired"() {
        expect:
        port.isAvailable()
    }

    def "listWorkflows projects AgentPlatform.agents() to WorkflowDescriptors"() {
        given:
        agentPlatform.agents() >> [makeAgent("invoice-classifier"), makeAgent("po-extractor")]

        when:
        List<WorkflowDescriptor> workflows = port.listWorkflows()

        then:
        workflows.size() == 2
        workflows*.name() == ["invoice-classifier", "po-extractor"]
    }

    def "execute returns success with serialized JSON when the agent completes"() {
        given:
        Agent agent = makeAgent("TestAgent")
        AgentProcess process = Mock()
        Blackboard blackboard = Mock()

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "hello"]) >> process
        process.getStatus() >> AgentProcessStatusCode.COMPLETED
        process.getBlackboard() >> blackboard
        blackboard.lastResult() >> [summary: "ok", topics: ["a", "b"]]

        when:
        OrchestrationResult result = port.execute("TestAgent", ["it": "hello"]).get()

        then:
        result.success()
        result.output().contains("ok")
        result.output().contains("a")
    }

    def "execute returns plain String when blackboard result is already a String"() {
        given:
        Agent agent = makeAgent("TextAgent")
        AgentProcess process = Mock()
        Blackboard blackboard = Mock()

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "input"]) >> process
        process.getStatus() >> AgentProcessStatusCode.COMPLETED
        process.getBlackboard() >> blackboard
        blackboard.lastResult() >> "plain result"

        when:
        OrchestrationResult result = port.execute("TextAgent", ["it": "input"]).get()

        then:
        result.success()
        result.output() == "plain result"
    }

    def "execute returns failure when the process terminates in a non-COMPLETED state"() {
        given:
        Agent agent = makeAgent("FailAgent")
        AgentProcess process = Mock()

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "input"]) >> process
        process.getStatus() >> AgentProcessStatusCode.FAILED
        process.getFailureInfo() >> "NullPointerException"

        when:
        OrchestrationResult result = port.execute("FailAgent", ["it": "input"]).get()

        then:
        !result.success()
        result.error().contains("NullPointerException")
    }

    def "execute returns failure when the blackboard has no last result"() {
        given:
        Agent agent = makeAgent("EmptyAgent")
        AgentProcess process = Mock()
        Blackboard blackboard = Mock()

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "input"]) >> process
        process.getStatus() >> AgentProcessStatusCode.COMPLETED
        process.getBlackboard() >> blackboard
        blackboard.lastResult() >> null

        when:
        OrchestrationResult result = port.execute("EmptyAgent", ["it": "input"]).get()

        then:
        !result.success()
        result.error().contains("no result")
    }

    def "execute returns failure when the agent name is not registered"() {
        given:
        agentPlatform.agents() >> [makeAgent("OtherAgent")]

        when:
        OrchestrationResult result = port.execute("MissingAgent", ["it": "input"]).get()

        then:
        !result.success()
        result.error().contains("MissingAgent")
    }

    private static Agent makeAgent(String name) {
        return new Agent(name, "test", "1.0.0", "Test agent: " + name,
                Collections.emptySet() as Set, Collections.emptyList() as List)
    }
}
