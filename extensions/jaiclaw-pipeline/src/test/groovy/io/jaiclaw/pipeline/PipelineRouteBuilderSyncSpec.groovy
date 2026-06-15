package io.jaiclaw.pipeline

import io.jaiclaw.pipeline.gateway.DefaultPipelineGateway
import io.jaiclaw.pipeline.gateway.PipelineExecutionResult
import io.jaiclaw.pipeline.gateway.PipelineSyncCoordinator
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import org.apache.camel.impl.DefaultCamelContext
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function

class PipelineRouteBuilderSyncSpec extends Specification {

    DefaultCamelContext camelContext
    PipelineSyncCoordinator coordinator
    PipelineRegistry registry
    DefaultPipelineGateway gateway

    private static PipelineDefinition pipe(String id, List<StageDefinition> stages) {
        return new PipelineDefinition(
                id, id, null, List.of(), true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 0, null,
                stages,
                new OutputDefinition(OutputType.LOG, null, null, null),
                null
        )
    }

    private static StageDefinition processorStage(String name, String beanRef) {
        return new StageDefinition(name, StageType.PROCESSOR, beanRef, null, null, null, null, null, null)
    }

    private void wirePipeline(PipelineDefinition definition, ApplicationContext appCtx) {
        BeanStageProcessor beanProcessor = new BeanStageProcessor(appCtx)
        PipelineRouteBuilder routeBuilder = new PipelineRouteBuilder(
                definition,
                PipelineProperties.PipelineDefaults.DEFAULT,
                null, beanProcessor, null,
                new PipelineAuditor(null, null),
                new PipelineHookFirer(null),
                null, null, null,
                new PipelineExecutionTracker(),
                coordinator
        )
        camelContext.addRoutes(routeBuilder)
        registry.register(definition)
    }

    def setup() {
        camelContext = new DefaultCamelContext()
        coordinator = new PipelineSyncCoordinator(PipelineProperties.SyncProperties.DEFAULT)
        registry = new PipelineRegistry()
        gateway = new DefaultPipelineGateway(camelContext.createProducerTemplate(), registry, coordinator)
    }

    def cleanup() {
        camelContext.stop()
        coordinator.shutdown()
    }

    def "triggerAndAwait returns SUCCESS with stage outputs"() {
        given:
        ApplicationContext appCtx = Mock()
        Function<String, String> upper = { input -> ((String) input).toUpperCase() } as Function
        Function<String, String> excl = { input -> input + "!" } as Function
        appCtx.getBean("upper") >> upper
        appCtx.getBean("excl") >> excl

        wirePipeline(
                pipe("p-sync", [processorStage("up", "upper"), processorStage("ex", "excl")]),
                appCtx)
        camelContext.start()

        when:
        PipelineExecutionResult result = gateway.triggerAndAwait("p-sync", "hello", Duration.ofSeconds(5))

        then:
        result.status() == ExecutionStatus.SUCCESS
        result.pipelineId() == "p-sync"
        result.stageOutputs()["up"] == "HELLO"
        result.stageOutputs()["ex"] == "HELLO!"
        result.input() == "hello"
        result.totalStages() == 2
        result.failureReason() == null
        coordinator.pendingCount() == 0
    }

    def "stage failure surfaces as FAILED result with partial stageOutputs"() {
        given:
        ApplicationContext appCtx = Mock()
        Function<String, String> upper = { input -> ((String) input).toUpperCase() } as Function
        Function<String, String> boom = { input -> throw new IllegalStateException("kaboom") } as Function
        appCtx.getBean("upper") >> upper
        appCtx.getBean("boom") >> boom

        wirePipeline(
                pipe("p-fail", [processorStage("up", "upper"), processorStage("boom", "boom")]),
                appCtx)
        camelContext.start()

        when:
        PipelineExecutionResult result = gateway.triggerAndAwait("p-fail", "hi", Duration.ofSeconds(5))

        then:
        result.status() == ExecutionStatus.FAILED
        result.failureReason() != null
        result.failureReason().contains("kaboom")
        result.stageOutputs()["up"] == "HI"      // first stage succeeded
        !result.stageOutputs().containsKey("boom") // failed stage did not advance the context
        coordinator.pendingCount() == 0
    }

    def "fire-and-forget trigger does not register with the coordinator"() {
        given:
        ApplicationContext appCtx = Mock()
        Function<String, String> upper = { input -> ((String) input).toUpperCase() } as Function
        appCtx.getBean("upper") >> upper

        wirePipeline(pipe("p-ff", [processorStage("up", "upper")]), appCtx)
        camelContext.start()

        when:
        gateway.trigger("p-ff", "hello")
        Thread.sleep(300)

        then: "no sync future was registered"
        coordinator.pendingCount() == 0
    }

    def "two concurrent triggerAsync executions complete independently"() {
        given:
        ApplicationContext appCtx = Mock()
        Function<String, String> upper = { input -> ((String) input).toUpperCase() } as Function
        appCtx.getBean("upper") >> upper

        wirePipeline(pipe("p-conc", [processorStage("up", "upper")]), appCtx)
        camelContext.start()

        when:
        CompletableFuture<PipelineExecutionResult> f1 = gateway.triggerAsync("p-conc", "a")
        CompletableFuture<PipelineExecutionResult> f2 = gateway.triggerAsync("p-conc", "b")
        PipelineExecutionResult r1 = f1.get(5, TimeUnit.SECONDS)
        PipelineExecutionResult r2 = f2.get(5, TimeUnit.SECONDS)

        then:
        r1.executionId() != r2.executionId()
        r1.stageOutputs()["up"] == "A"
        r2.stageOutputs()["up"] == "B"
        r1.status() == ExecutionStatus.SUCCESS
        r2.status() == ExecutionStatus.SUCCESS
        coordinator.pendingCount() == 0
    }
}
