package io.jaiclaw.pipeline

import io.jaiclaw.core.hook.event.AgentEndedEvent
import io.jaiclaw.core.hook.event.AgentStartedEvent
import io.jaiclaw.core.hook.event.PipelineExecutionCompletedEvent
import io.jaiclaw.core.hook.event.PipelineExecutionFailedEvent
import io.jaiclaw.core.hook.event.PipelineExecutionStartedEvent
import io.jaiclaw.core.hook.event.PipelineStageCompletedEvent
import io.jaiclaw.core.hook.event.PipelineStageFailedEvent
import io.jaiclaw.core.hook.event.PipelineStageStartedEvent
import io.jaiclaw.core.hook.event.ToolCallEndedEvent
import io.jaiclaw.core.hook.event.ToolCallStartedEvent
import io.jaiclaw.plugin.HookRunner
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

class PipelineHookFirerSpec extends Specification {

    HookRunner hookRunner = Mock()
    ApplicationEventPublisher publisher = Mock()
    PipelineHookFirer firer = new PipelineHookFirer(hookRunner, publisher)

    def ctx() {
        new PipelineContext("pipe-1", "exec-1", "tenant-x", "corr-1",
                0, 2, null, null, [:], [:])
    }

    def stage() {
        new StageDefinition("s1", StageType.PROCESSOR, "beanA", null, null, null, null, null, null, null, null)
    }

    def "firePipelineStart emits typed + legacy events and publishes typed to Spring"() {
        when:
        firer.firePipelineStart(ctx())

        then:
        1 * hookRunner.fireVoid({ it instanceof PipelineExecutionStartedEvent })
        1 * hookRunner.fireVoid({ it instanceof AgentStartedEvent })
        1 * publisher.publishEvent({ it instanceof PipelineExecutionStartedEvent })
    }

    def "fireStageStart emits typed + legacy events"() {
        when:
        firer.fireStageStart(ctx(), stage())

        then:
        1 * hookRunner.fireVoid({ it instanceof PipelineStageStartedEvent })
        1 * hookRunner.fireVoid({ it instanceof ToolCallStartedEvent })
        1 * publisher.publishEvent({ it instanceof PipelineStageStartedEvent })
    }

    def "fireStageComplete emits typed + legacy events with stage duration"() {
        given:
        firer.fireStageStart(ctx(), stage())

        when:
        firer.fireStageComplete(ctx(), stage(), "result")

        then:
        1 * hookRunner.fireVoid({
            it instanceof PipelineStageCompletedEvent &&
                    ((PipelineStageCompletedEvent) it).resultPreview() == "result" &&
                    ((PipelineStageCompletedEvent) it).durationMs() >= 0L
        })
        1 * hookRunner.fireVoid({ it instanceof ToolCallEndedEvent })
        1 * publisher.publishEvent({ it instanceof PipelineStageCompletedEvent })
    }

    def "fireStageFailed emits typed event only (no legacy)"() {
        when:
        firer.fireStageFailed(ctx(), stage(), new RuntimeException("bang"))

        then:
        1 * hookRunner.fireVoid({
            it instanceof PipelineStageFailedEvent &&
                    ((PipelineStageFailedEvent) it).failureReason().contains("bang")
        })
        0 * hookRunner.fireVoid(_ as ToolCallEndedEvent)
        1 * publisher.publishEvent({ it instanceof PipelineStageFailedEvent })
    }

    def "firePipelineEnd emits typed + legacy events"() {
        given:
        firer.firePipelineStart(ctx())

        when:
        firer.firePipelineEnd(ctx())

        then:
        // firePipelineStart already fired PipelineExecutionStartedEvent + AgentStartedEvent above.
        1 * hookRunner.fireVoid({
            it instanceof PipelineExecutionCompletedEvent &&
                    ((PipelineExecutionCompletedEvent) it).totalStages() == 2 &&
                    ((PipelineExecutionCompletedEvent) it).totalDurationMs() >= 0L
        })
        1 * hookRunner.fireVoid({ it instanceof AgentEndedEvent })
        1 * publisher.publishEvent({ it instanceof PipelineExecutionCompletedEvent })
    }

    def "firePipelineFailed emits typed event only (no legacy)"() {
        given:
        firer.firePipelineStart(ctx())

        when:
        firer.firePipelineFailed(ctx(), "s1", new IllegalStateException("kaboom"))

        then:
        // firePipelineStart consumed already.
        1 * hookRunner.fireVoid({
            it instanceof PipelineExecutionFailedEvent &&
                    ((PipelineExecutionFailedEvent) it).failedStage() == "s1" &&
                    ((PipelineExecutionFailedEvent) it).failureReason().contains("kaboom")
        })
        0 * hookRunner.fireVoid(_ as AgentEndedEvent)
        1 * publisher.publishEvent({ it instanceof PipelineExecutionFailedEvent })
    }

    def "no-op cleanly when hookRunner and publisher are null"() {
        given:
        def bare = new PipelineHookFirer(null, null)

        when:
        bare.firePipelineStart(ctx())
        bare.fireStageStart(ctx(), stage())
        bare.fireStageComplete(ctx(), stage(), "x")
        bare.fireStageFailed(ctx(), stage(), new RuntimeException("x"))
        bare.firePipelineEnd(ctx())
        bare.firePipelineFailed(ctx(), null, new RuntimeException("x"))

        then:
        noExceptionThrown()
    }

    def "publish failure does not break the firer"() {
        given:
        publisher.publishEvent(_) >> { throw new RuntimeException("listener boom") }

        when:
        firer.firePipelineStart(ctx())

        then:
        1 * hookRunner.fireVoid({ it instanceof PipelineExecutionStartedEvent })
        1 * hookRunner.fireVoid({ it instanceof AgentStartedEvent })
        noExceptionThrown()
    }
}
