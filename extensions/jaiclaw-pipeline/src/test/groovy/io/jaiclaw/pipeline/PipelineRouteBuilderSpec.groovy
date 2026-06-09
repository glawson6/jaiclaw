package io.jaiclaw.pipeline

import org.apache.camel.CamelContext
import org.apache.camel.Exchange
import org.apache.camel.ProducerTemplate
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.impl.DefaultCamelContext
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.Function

class PipelineRouteBuilderSpec extends Specification {

    def "builds routes for a simple processor pipeline"() {
        given:
        DefaultCamelContext camelContext = new DefaultCamelContext()

        ApplicationContext appCtx = Mock()
        Function<String, String> upperCase = { input -> input.toUpperCase() } as Function
        appCtx.getBean("upperCase") >> upperCase

        BeanStageProcessor beanProcessor = new BeanStageProcessor(appCtx)

        PipelineDefinition definition = new PipelineDefinition(
                "test-pipe", "Test Pipeline", null, List.of(), true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 0, null,
                [new StageDefinition("upper", StageType.PROCESSOR, "upperCase", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.LOG, null, null, null),
                null
        )

        PipelineRouteBuilder routeBuilder = new PipelineRouteBuilder(
                definition,
                PipelineProperties.PipelineDefaults.DEFAULT,
                null, beanProcessor, null,
                new PipelineAuditor(null, null),
                new PipelineHookFirer(null),
                null, null, null
        )

        camelContext.addRoutes(routeBuilder)
        camelContext.start()

        when:
        ProducerTemplate template = camelContext.createProducerTemplate()
        template.sendBody("direct:pipeline-test-pipe", "hello world")

        // Allow SEDA processing
        Thread.sleep(500)

        then:
        noExceptionThrown()

        cleanup:
        camelContext.stop()
    }

    def "stageQueueUri uses configurable SEDA parameters"() {
        given:
        PipelineProperties.PipelineDefaults defaults = new PipelineProperties.PipelineDefaults(200, 10, false)
        StageDefinition stage = new StageDefinition(
                "s1", StageType.PROCESSOR, "p1", null, null, null, null, null, null
        )
        PipelineDefinition definition = new PipelineDefinition(
                "test", null, null, List.of(), true, null, null, 0, null,
                [stage], null, null
        )

        PipelineRouteBuilder builder = new PipelineRouteBuilder(
                definition, defaults,
                null, null, null, null, null, null, null, null
        )

        when:
        String uri = builder.stageQueueUri("test", 0, stage)

        then:
        uri.contains("size=200")
        uri.contains("concurrentConsumers=10")
        uri.contains("blockWhenFull=false")
    }

    def "stageQueueUri uses per-stage transport override"() {
        given:
        StageDefinition stage = new StageDefinition(
                "s1", StageType.PROCESSOR, "p1", null, null, null, null, null,
                new StageDefinition.TransportConfig("kafka:my-topic?brokers=kafka:9092", null)
        )
        PipelineDefinition definition = new PipelineDefinition(
                "test", null, null, List.of(), true, null, null, 0, null,
                [stage], null, null
        )

        PipelineRouteBuilder builder = new PipelineRouteBuilder(
                definition, PipelineProperties.PipelineDefaults.DEFAULT,
                null, null, null, null, null, null, null, null
        )

        when:
        String uri = builder.stageQueueUri("test", 0, stage)

        then:
        uri == "kafka:my-topic?brokers=kafka:9092"
    }

    def "multi-stage pipeline advances context through stages"() {
        given:
        DefaultCamelContext camelContext = new DefaultCamelContext()

        ApplicationContext appCtx = Mock()
        Function<String, String> addPrefix = { input -> "prefix-" + input } as Function
        Function<String, String> addSuffix = { input -> input + "-suffix" } as Function
        appCtx.getBean("addPrefix") >> addPrefix
        appCtx.getBean("addSuffix") >> addSuffix

        BeanStageProcessor beanProcessor = new BeanStageProcessor(appCtx)

        PipelineDefinition definition = new PipelineDefinition(
                "multi-pipe", "Multi Stage", null, List.of(), true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 0, null,
                [
                        new StageDefinition("first", StageType.PROCESSOR, "addPrefix", null, null, null, null, null, null),
                        new StageDefinition("second", StageType.PROCESSOR, "addSuffix", null, null, null, null, null, null),
                ],
                new OutputDefinition(OutputType.CAMEL_URI, null, "mock:result", null),
                null
        )

        PipelineRouteBuilder routeBuilder = new PipelineRouteBuilder(
                definition,
                PipelineProperties.PipelineDefaults.DEFAULT,
                null, beanProcessor, null,
                new PipelineAuditor(null, null),
                new PipelineHookFirer(null),
                null, null, null
        )

        camelContext.addRoutes(routeBuilder)
        camelContext.start()

        MockEndpoint mockEndpoint = camelContext.getEndpoint("mock:result", MockEndpoint.class)
        mockEndpoint.expectedMessageCount(1)

        when:
        ProducerTemplate template = camelContext.createProducerTemplate()
        template.sendBody("direct:pipeline-multi-pipe", "hello")

        // Allow SEDA processing
        mockEndpoint.assertIsSatisfied(5000)

        then:
        mockEndpoint.receivedExchanges.size() == 1
        mockEndpoint.receivedExchanges[0].getIn().getBody(String.class) == "prefix-hello-suffix"

        cleanup:
        camelContext.stop()
    }
}
