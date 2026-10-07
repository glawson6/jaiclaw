package io.jaiclaw.embabel.delegate

import com.embabel.agent.core.Agent
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.AgentProcess
import com.embabel.agent.core.AgentProcessStatusCode
import com.embabel.agent.core.Blackboard
import com.embabel.agent.core.ProcessOptions
import tools.jackson.databind.ObjectMapper
import io.jaiclaw.tools.bridge.embabel.OrchestrationResult
import io.jaiclaw.tools.bridge.embabel.WorkflowDescriptor
import spock.lang.Specification
import spock.lang.Subject

import java.net.URL
import java.net.URLClassLoader

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

    def "execute pins the thread context class loader to this module's loader for the Embabel call"() {
        given: "Embabel's JvmType resolves @Action types via the TCCL; the common pool's is the JDK app loader"
        Agent agent = makeAgent("LoaderAgent")
        AgentProcess process = Mock()
        Blackboard blackboard = Mock()
        ClassLoader seenDuringCall = null

        agentPlatform.agents() >> [agent]
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "input"]) >> {
            seenDuringCall = Thread.currentThread().contextClassLoader
            process
        }
        process.getStatus() >> AgentProcessStatusCode.COMPLETED
        process.getBlackboard() >> blackboard
        blackboard.lastResult() >> "ok"

        when: "the call runs on a pool thread whose TCCL cannot see application classes"
        OrchestrationResult result = port.execute("LoaderAgent", ["it": "input"]).get()

        then: "inside the Embabel call the loader is the one that loaded the delegate"
        result.success()
        seenDuringCall.is(EmbabelInvocations.classLoader)
    }

    def "run restores the caller's context class loader afterwards, even on failure"() {
        given: "a thread carrying a deliberately empty loader"
        Agent agent = makeAgent("RestoreAgent")
        ClassLoader empty = new URLClassLoader(new URL[0], (ClassLoader) null)
        ClassLoader seenDuringCall = null
        ClassLoader seenAfterCall = null
        agentPlatform.runAgentFrom(agent, ProcessOptions.DEFAULT, ["it": "x"]) >> {
            seenDuringCall = Thread.currentThread().contextClassLoader
            throw new IllegalStateException("boom")
        }

        when:
        Thread t = new Thread({
            Thread.currentThread().contextClassLoader = empty
            try {
                EmbabelInvocations.run(agentPlatform, agent, ["it": "x"])
            } catch (IllegalStateException ignored) {
            }
            seenAfterCall = Thread.currentThread().contextClassLoader
        })
        t.start()
        t.join(5000)

        then: "pinned for the call, and the empty loader is back once it returns"
        seenDuringCall.is(EmbabelInvocations.classLoader)
        seenAfterCall.is(empty)
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
