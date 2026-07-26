package io.jaiclaw.pipeline

import io.jaiclaw.core.hook.event.PipelineExecutionCompletedEvent
import io.jaiclaw.plugin.HookRunner
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification

/**
 * Verifies that {@link PipelineHookFirer#firePipelineEnd(PipelineContext, String)}
 * threads the result string into the emitted
 * {@link PipelineExecutionCompletedEvent}, and that the backward-compat
 * {@code firePipelineEnd(ctx)} form still works with {@code result = null}.
 */
class PipelineHookFirerResultSpec extends Specification {

    HookRunner hookRunner = Mock()
    ApplicationEventPublisher publisher = Mock()
    PipelineHookFirer firer = new PipelineHookFirer(hookRunner, publisher)

    def ctx() {
        new PipelineContext("pipe-1", "exec-1", "tenant-x", "corr-1",
                0, 2, null, null, [:], [:])
    }

    def "firePipelineEnd(ctx, result) emits event carrying the result"() {
        when:
        firer.firePipelineEnd(ctx(), "hello-result")

        then:
        1 * hookRunner.fireVoid({
            it instanceof PipelineExecutionCompletedEvent && it.result() == "hello-result"
        })
        1 * publisher.publishEvent({
            it instanceof PipelineExecutionCompletedEvent && it.result() == "hello-result"
        })
        _ * hookRunner.fireVoid(_)   // legacy AgentEndedEvent
    }

    def "firePipelineEnd(ctx) — old 1-arg overload — emits event with null result"() {
        when:
        firer.firePipelineEnd(ctx())

        then:
        1 * hookRunner.fireVoid({
            it instanceof PipelineExecutionCompletedEvent && it.result() == null
        })
        1 * publisher.publishEvent({
            it instanceof PipelineExecutionCompletedEvent && it.result() == null
        })
        _ * hookRunner.fireVoid(_)
    }
}
