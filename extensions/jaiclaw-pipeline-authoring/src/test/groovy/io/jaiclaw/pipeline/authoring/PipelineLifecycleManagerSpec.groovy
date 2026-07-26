package io.jaiclaw.pipeline.authoring

import io.jaiclaw.core.hook.event.PipelineDeployedEvent
import io.jaiclaw.core.hook.event.PipelineUndeployedEvent
import io.jaiclaw.pipeline.BeanStageProcessor
import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.OutputDefinition
import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.PipelineSecurityProperties
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import io.jaiclaw.pipeline.validation.PipelineValidator
import io.jaiclaw.pipeline.validation.ValidationReport
import io.jaiclaw.plugin.HookRunner
import org.apache.camel.CamelContext
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

import java.time.Duration

/**
 * Unit spec for {@link PipelineLifecycleManager}. Every Camel dependency
 * is mocked — the integration spec covers the real add-routes flow via
 * DefaultCamelContext.
 */
class PipelineLifecycleManagerSpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()
    PipelineValidator validator = Mock()
    CamelContext camelContext = Mock()
    BeanStageProcessor beanProcessor = Mock()
    HookRunner hookRunner = Mock()
    ApplicationEventPublisher publisher = Mock()

    PipelineLifecycleManager manager = new PipelineLifecycleManager(
            registry, validator, null, camelContext,
            null, beanProcessor, null,
            null, null, null,
            null, null,
            null, null,
            hookRunner, publisher,
            Duration.ofSeconds(10))

    private static PipelineDefinition definitionOf(String id) {
        return new PipelineDefinition(
                id, id, null, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                null)
    }

    def "deploy on a fresh id registers, adds routes, and fires PipelineDeployedEvent"() {
        given:
        PipelineDefinition pipeline = definitionOf("p1")
        validator.validate(pipeline) >> new ValidationReport.Builder().build()

        when:
        PipelineDefinition returned = manager.deploy(pipeline, "STUDIO")

        then:
        1 * camelContext.addRoutes(_)
        1 * hookRunner.fireVoid({ PipelineDeployedEvent e ->
            e.pipelineId() == "p1" && e.origin() == "STUDIO" && e.stageCount() == 1
        })
        1 * publisher.publishEvent({ it instanceof PipelineDeployedEvent })
        registry.contains("p1")
        returned.id() == "p1"
    }

    def "deploy on an already-deployed id throws AlreadyDeployedException"() {
        given:
        PipelineDefinition pipeline = definitionOf("p1")
        registry.replace(pipeline)   // simulate a prior deploy

        when:
        manager.deploy(pipeline, "STUDIO")

        then:
        thrown(PipelineLifecycleManager.AlreadyDeployedException)
        0 * camelContext.addRoutes(_)
    }

    def "deploy of an invalid definition rejects without touching registry"() {
        given:
        PipelineDefinition pipeline = definitionOf("p1")
        ValidationReport.Builder b = new ValidationReport.Builder()
        b.addPipelineError("p1", new io.jaiclaw.pipeline.validation.ValidationError(
                "p1", "stage 's1'", "X", "y", null))
        validator.validate(pipeline) >> b.build()

        when:
        manager.deploy(pipeline, "STUDIO")

        then:
        thrown(IllegalArgumentException)
        !registry.contains("p1")
        0 * camelContext.addRoutes(_)
    }

    def "deploy rolls back registry when addRoutes fails"() {
        given:
        PipelineDefinition pipeline = definitionOf("p1")
        validator.validate(pipeline) >> new ValidationReport.Builder().build()
        camelContext.addRoutes(_) >> { throw new RuntimeException("boom") }

        when:
        manager.deploy(pipeline, "STUDIO")

        then:
        thrown(IllegalStateException)
        !registry.contains("p1")
        0 * hookRunner.fireVoid({ it instanceof PipelineDeployedEvent })
    }

    def "undeploy returns empty and skips events when nothing was deployed"() {
        when:
        Optional<PipelineDefinition> removed = manager.undeploy("nope", "undeploy")

        then:
        !removed.isPresent()
        0 * hookRunner.fireVoid(_)
    }

    def "undeploy removes routes matching pipeline-{id}-* prefix"() {
        given:
        PipelineDefinition pipeline = definitionOf("p1")
        registry.replace(pipeline)
        camelContext.getRoutes() >> [
                Mock(org.apache.camel.Route) { getId() >> "pipeline-p1-trigger" },
                Mock(org.apache.camel.Route) { getId() >> "pipeline-p1-stage-s1" },
                Mock(org.apache.camel.Route) { getId() >> "pipeline-other-trigger" }
        ]
        def controller = Mock(org.apache.camel.spi.RouteController)
        camelContext.getRouteController() >> controller

        when:
        def removed = manager.undeploy("p1", "undeploy")

        then:
        1 * controller.stopRoute("pipeline-p1-trigger", _, _)
        1 * controller.stopRoute("pipeline-p1-stage-s1", _, _)
        0 * controller.stopRoute("pipeline-other-trigger", _, _)
        1 * camelContext.removeRoute("pipeline-p1-trigger")
        1 * camelContext.removeRoute("pipeline-p1-stage-s1")
        1 * hookRunner.fireVoid({ PipelineUndeployedEvent e ->
            e.pipelineId() == "p1" && e.reason() == "undeploy"
        })
        removed.isPresent()
        !registry.contains("p1")
    }

    def "redeploy id mismatch throws"() {
        given:
        PipelineDefinition next = definitionOf("other")

        when:
        manager.redeploy("p1", next, "STUDIO")

        then:
        thrown(IllegalArgumentException)
    }

    def "isDeployed reflects registry state"() {
        given:
        registry.replace(definitionOf("p1"))

        expect:
        manager.isDeployed("p1")
        !manager.isDeployed("p2")
        !manager.isDeployed(null)
    }
}
