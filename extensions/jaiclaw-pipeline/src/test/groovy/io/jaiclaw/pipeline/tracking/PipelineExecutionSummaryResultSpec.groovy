package io.jaiclaw.pipeline.tracking

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

/**
 * Verifies the new {@code result} field on {@link PipelineExecutionSummary}
 * — the 4 KB truncation, the 3-arg {@code completedSuccessfully} overload,
 * and the null-on-failure invariant.
 */
class PipelineExecutionSummaryResultSpec extends Specification {

    private static PipelineExecutionSummary running() {
        return new PipelineExecutionSummary(
                "exec-1", "pipe-1", null,
                Instant.now(), null, ExecutionStatus.RUNNING, null,
                [:] as Map, null, null, null)
    }

    def "the 10-arg backward-compat constructor defaults result to null"() {
        given:
        Instant now = Instant.now()

        when:
        PipelineExecutionSummary s = new PipelineExecutionSummary(
                "exec-1", "pipe-1", null,
                now, null, ExecutionStatus.RUNNING, null,
                [:] as Map, null, null)

        then:
        s.result() == null
    }

    def "completedSuccessfully(when, total) — old 2-arg overload — sets result null"() {
        given:
        PipelineExecutionSummary running = running()

        when:
        PipelineExecutionSummary done = running.completedSuccessfully(Instant.now(), Duration.ofMillis(120))

        then:
        done.status() == ExecutionStatus.SUCCESS
        done.result() == null
    }

    def "completedSuccessfully(when, total, result) — new 3-arg overload — carries result"() {
        given:
        PipelineExecutionSummary running = running()

        when:
        PipelineExecutionSummary done = running.completedSuccessfully(
                Instant.now(), Duration.ofMillis(120), "hello-result")

        then:
        done.status() == ExecutionStatus.SUCCESS
        done.result() == "hello-result"
    }

    def "result > 4 KB is truncated with the same marker as failureReason"() {
        given:
        String big = "x" * (PipelineExecutionSummary.MAX_RESULT_BYTES + 500)

        when:
        PipelineExecutionSummary done = running().completedSuccessfully(
                Instant.now(), Duration.ofMillis(1), big)

        then:
        done.result().length() == PipelineExecutionSummary.MAX_RESULT_BYTES + "…[truncated]".length()
        done.result().endsWith("…[truncated]")
    }

    def "completedWithFailure always sets result null even if the summary already had one"() {
        given:
        PipelineExecutionSummary running = running()
        PipelineExecutionSummary withResult = running.completedSuccessfully(
                Instant.now(), Duration.ofMillis(1), "some-result")

        when:
        PipelineExecutionSummary failed = withResult.completedWithFailure(
                Instant.now(), "boom", Duration.ofMillis(1))

        then:
        failed.status() == ExecutionStatus.FAILED
        failed.result() == null
        failed.failureReason() == "boom"
    }

    def "withCurrentStage and withStageDuration preserve the result field"() {
        given:
        PipelineExecutionSummary done = running().completedSuccessfully(
                Instant.now(), Duration.ofMillis(1), "carry-through")

        expect:
        done.withCurrentStage("s1").result() == "carry-through"
        done.withStageDuration("s1", Duration.ofMillis(5)).result() == "carry-through"
    }
}
