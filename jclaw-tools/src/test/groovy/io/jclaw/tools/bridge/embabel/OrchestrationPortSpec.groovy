package io.jclaw.tools.bridge.embabel

import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class OrchestrationPortSpec extends Specification {

    def "NoOpOrchestrationPort is not available"() {
        given:
        def port = new NoOpOrchestrationPort()

        expect:
        !port.isAvailable()
        port.platformName() == "none"
        port.listWorkflows().isEmpty()
    }

    def "NoOpOrchestrationPort execute returns failure"() {
        given:
        def port = new NoOpOrchestrationPort()

        when:
        def result = port.execute("test-workflow", [:]).join()

        then:
        !result.success()
        result.error() == "No orchestration platform configured"
    }

    def "OrchestrationResult success factory"() {
        when:
        def result = OrchestrationResult.success("output text")

        then:
        result.success()
        result.output() == "output text"
        result.error() == null
        result.metadata() == [:]
    }

    def "OrchestrationResult success with metadata"() {
        when:
        def result = OrchestrationResult.success("output", [key: "value"])

        then:
        result.success()
        result.metadata().key == "value"
    }

    def "OrchestrationResult failure factory"() {
        when:
        def result = OrchestrationResult.failure("something broke")

        then:
        !result.success()
        result.error() == "something broke"
        result.output() == ""
    }

    def "WorkflowDescriptor of factory"() {
        when:
        def desc = WorkflowDescriptor.of("analyze", "Analyze data")

        then:
        desc.name() == "analyze"
        desc.description() == "Analyze data"
        desc.inputSchema() == null
    }

    def "WorkflowDescriptor rejects blank name"() {
        when:
        new WorkflowDescriptor("", "desc", null)

        then:
        thrown(IllegalArgumentException)
    }

    def "Custom AgentOrchestrationPort implementation"() {
        given:
        def customPort = new AgentOrchestrationPort() {
            CompletableFuture<OrchestrationResult> execute(String name, Map<String, Object> input) {
                return CompletableFuture.completedFuture(
                        OrchestrationResult.success("Ran $name with ${input.size()} inputs"))
            }
            List<WorkflowDescriptor> listWorkflows() {
                return [WorkflowDescriptor.of("w1", "Workflow 1")]
            }
            boolean isAvailable() { return true }
            String platformName() { return "embabel" }
        }

        expect:
        customPort.isAvailable()
        customPort.platformName() == "embabel"
        customPort.listWorkflows().size() == 1

        when:
        def result = customPort.execute("w1", [a: 1]).join()

        then:
        result.success()
        result.output().contains("w1")
    }
}
