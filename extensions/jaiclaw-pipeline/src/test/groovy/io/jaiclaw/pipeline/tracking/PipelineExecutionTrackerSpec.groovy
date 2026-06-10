package io.jaiclaw.pipeline.tracking

import io.jaiclaw.pipeline.PipelineContext
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PipelineExecutionTrackerSpec extends Specification {

    private static PipelineContext ctx(String executionId, String pipelineId = "p", String tenantId = null) {
        return new PipelineContext(pipelineId, executionId, tenantId, "corr",
                0, 1, null, null, Map.of(), Map.of())
    }

    def "records start and exposes via byId/recent"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker(10)

        when:
        tracker.started(ctx("e1"))

        then:
        tracker.byId("e1").get().status() == ExecutionStatus.RUNNING
        tracker.recent("p").size() == 1
        tracker.recent("p")[0].executionId() == "e1"
    }

    def "stage start updates currentStage"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker()
        tracker.started(ctx("e1"))

        when:
        tracker.stageStarted(ctx("e1"), "research")

        then:
        tracker.byId("e1").get().currentStage() == "research"
    }

    def "stageCompleted records duration"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker()
        tracker.started(ctx("e1"))

        when:
        tracker.stageCompleted(ctx("e1"), "research", Duration.ofMillis(123))

        then:
        tracker.byId("e1").get().stageDurations().get("research") == Duration.ofMillis(123)
    }

    def "succeeded marks SUCCESS and sets totalDuration"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker()
        tracker.started(ctx("e1"))

        when:
        tracker.succeeded(ctx("e1"), Duration.ofMillis(900))

        then:
        PipelineExecutionSummary s = tracker.byId("e1").get()
        s.status() == ExecutionStatus.SUCCESS
        s.totalDuration() == Duration.ofMillis(900)
        s.completedAt() != null
        s.currentStage() == null
    }

    def "failed marks FAILED with reason"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker()
        tracker.started(ctx("e1"))
        tracker.stageStarted(ctx("e1"), "write")

        when:
        tracker.failed(ctx("e1"), "boom", Duration.ofMillis(500))

        then:
        PipelineExecutionSummary s = tracker.byId("e1").get()
        s.status() == ExecutionStatus.FAILED
        s.failureReason() == "boom"
        s.currentStage() == "write"
    }

    def "bounded per pipeline: oldest entry evicted past max"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker(3)
        tracker.started(ctx("e1"))
        tracker.started(ctx("e2"))
        tracker.started(ctx("e3"))

        when:
        tracker.started(ctx("e4"))

        then:
        tracker.recent("p")*.executionId() == ["e2", "e3", "e4"]
        !tracker.byId("e1").isPresent()
        tracker.byId("e4").isPresent()
    }

    def "separate pipelines tracked independently"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker()

        when:
        tracker.started(ctx("a1", "alpha"))
        tracker.started(ctx("b1", "beta"))
        tracker.started(ctx("a2", "alpha"))

        then:
        tracker.recent("alpha").size() == 2
        tracker.recent("beta").size() == 1
        tracker.pipelineIds().toSorted() == ["alpha", "beta"]
    }

    def "concurrent inserts maintain bound and consistency"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker(20)
        def pool = Executors.newFixedThreadPool(4)
        def latch = new CountDownLatch(4)

        when:
        for (int t = 0; t < 4; t++) {
            int threadId = t
            pool.submit {
                try {
                    for (int i = 0; i < 50; i++) {
                        tracker.started(ctx("t${threadId}-e${i}"))
                    }
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await(5, TimeUnit.SECONDS)
        pool.shutdown()

        then:
        tracker.recent("p").size() == 20
        tracker.sizesByPipeline().get("p") == 20

        cleanup:
        pool.shutdownNow()
    }

    def "byId returns empty for unknown id"() {
        expect:
        !new PipelineExecutionTracker().byId("nope").isPresent()
    }

    def "clear wipes state"() {
        given:
        PipelineExecutionTracker tracker = new PipelineExecutionTracker()
        tracker.started(ctx("e1"))

        when:
        tracker.clear()

        then:
        tracker.size() == 0
        tracker.recent("p").isEmpty()
    }
}
