package io.jaiclaw.pipeline.gateway

import io.jaiclaw.pipeline.OutputDefinition
import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.apache.camel.ProducerTemplate
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import spock.lang.Specification

class DefaultPipelineGatewaySpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()
    ProducerTemplate producer = Mock()
    DefaultPipelineGateway gateway = new DefaultPipelineGateway(producer, registry)
    DefaultCamelContext camelContext = new DefaultCamelContext()

    private static PipelineDefinition pipe(String id, boolean enabled = true) {
        return new PipelineDefinition(id, id, null, List.of(), enabled,
                null, null, 0, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.LOG, null, null, null), null)
    }

    private Exchange runProcessor(Processor processor) {
        DefaultExchange ex = new DefaultExchange(camelContext)
        processor.process(ex)
        return ex
    }

    def "trigger returns handle and submits to direct:pipeline-<id>"() {
        given:
        registry.register(pipe("p1"))
        String capturedUri = null
        Processor capturedProcessor = null

        when:
        PipelineExecutionHandle handle = gateway.trigger("p1", "hello")

        then:
        1 * producer.asyncSend("direct:pipeline-p1", _ as Processor) >> { String uri, Processor p ->
            capturedUri = uri
            capturedProcessor = p
            null
        }
        handle.pipelineId() == "p1"
        handle.executionId() != null
        capturedUri == "direct:pipeline-p1"

        when:
        Exchange ex = runProcessor(capturedProcessor)

        then:
        ex.getIn().getBody(String.class) == "hello"
        ex.getIn().getHeader(DefaultPipelineGateway.HEADER_GATEWAY_EXECUTION_ID) == handle.executionId()
        ex.getIn().getHeader(DefaultPipelineGateway.HEADER_TENANT_ID) == null
        ex.getIn().getHeader(DefaultPipelineGateway.HEADER_CORRELATION_ID) == null
    }

    def "trigger with tenantId and correlationId sets headers"() {
        given:
        registry.register(pipe("p1"))
        Processor capturedProcessor = null

        when:
        gateway.trigger("p1", "hi", "tenant-a", "corr-99")

        then:
        1 * producer.asyncSend("direct:pipeline-p1", _ as Processor) >> { String uri, Processor p ->
            capturedProcessor = p
            null
        }

        when:
        Exchange ex = runProcessor(capturedProcessor)

        then:
        ex.getIn().getHeader(DefaultPipelineGateway.HEADER_TENANT_ID) == "tenant-a"
        ex.getIn().getHeader(DefaultPipelineGateway.HEADER_CORRELATION_ID) == "corr-99"
    }

    def "unknown pipeline throws"() {
        when:
        gateway.trigger("missing", "body")

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("missing")
        0 * producer._
    }

    def "disabled pipeline throws"() {
        given:
        registry.register(pipe("p1", false))

        when:
        gateway.trigger("p1", "body")

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("disabled")
        0 * producer._
    }
}
