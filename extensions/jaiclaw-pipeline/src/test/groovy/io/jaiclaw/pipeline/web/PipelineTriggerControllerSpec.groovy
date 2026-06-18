package io.jaiclaw.pipeline.web

import io.jaiclaw.pipeline.PipelineProperties
import io.jaiclaw.pipeline.PipelineProperties.HttpTriggerProperties
import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle
import io.jaiclaw.pipeline.gateway.PipelineGateway
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

/**
 * Covers the alias-routed {@code POST /trigger} and the consumer-safe
 * {@code GET /status/{id}} endpoints. The old {@code /{id}/trigger}
 * route was removed deliberately to keep pipeline ids out of the URL
 * surface; this spec asserts the new contract end-to-end at the
 * controller layer.
 */
class PipelineTriggerControllerSpec extends Specification {

    PipelineGateway gateway = Mock()
    PipelineExecutionTracker tracker = Mock()
    ObjectProvider<PipelineExecutionTracker> trackerProvider = Mock {
        getIfAvailable() >> tracker
    }

    private PipelineTriggerController controller(Map<String, String> allowed = ["ticket-scoring": "embabel-pipe"]) {
        HttpTriggerProperties http = new HttpTriggerProperties(true, "/api/pipelines", allowed)
        PipelineProperties props = Mock {
            httpTrigger() >> http
        }
        return new PipelineTriggerController(gateway, props, trackerProvider)
    }

    // ── POST /trigger ──────────────────────────────────────────────────────

    def "alias routes to the configured internal pipeline and returns an opaque TriggerResponse"() {
        given:
        PipelineExecutionHandle handle = new PipelineExecutionHandle("exec-1", "embabel-pipe", Instant.parse("2026-06-18T00:00:00Z"))
        gateway.trigger("embabel-pipe", "priority:high size:large", "tenant-a", "corr-z") >> handle
        PipelineTriggerRequest request = new PipelineTriggerRequest(
                "ticket-scoring", "priority:high size:large", null, null, null)

        when:
        ResponseEntity<?> response = controller().trigger(request, "tenant-a", "corr-z")

        then:
        response.statusCode == HttpStatus.ACCEPTED
        TriggerResponse body = response.body as TriggerResponse
        body.id() == "exec-1"
        body.submittedAt() == Instant.parse("2026-06-18T00:00:00Z")
        // Opaque on purpose — the internal pipeline id never leaves the controller.
        !(response.body instanceof PipelineExecutionHandle)
    }

    def "blank pipeline alias returns 400"() {
        given:
        PipelineTriggerRequest request = new PipelineTriggerRequest("  ", "body", null, null, null)

        when:
        ResponseEntity<?> response = controller().trigger(request, null, null)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        (response.body as PipelineTriggerController.ErrorBody).error().contains("'pipeline'")
        0 * gateway.trigger(_, _, _, _)
    }

    def "null request body returns 400"() {
        when:
        ResponseEntity<?> response = controller().trigger(null, null, null)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        (response.body as PipelineTriggerController.ErrorBody).error().contains("Request body")
        0 * gateway.trigger(_, _, _, _)
    }

    def "unknown alias returns 404 — never reaches the gateway"() {
        given:
        PipelineTriggerRequest request = new PipelineTriggerRequest("nonexistent", "body", null, null, null)

        when:
        ResponseEntity<?> response = controller().trigger(request, null, null)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        (response.body as PipelineTriggerController.ErrorBody).error() == "Unknown pipeline: nonexistent"
        0 * gateway.trigger(_, _, _, _)
    }

    def "alias mapped to a non-existent pipeline returns 500 with a misconfiguration message"() {
        given:
        PipelineTriggerController c = controller(["ticket-scoring": "missing-pipe"])
        gateway.trigger("missing-pipe", _, _, _) >> {
            throw new IllegalArgumentException("Unknown pipeline: 'missing-pipe'")
        }
        PipelineTriggerRequest request = new PipelineTriggerRequest("ticket-scoring", "x", null, null, null)

        when:
        ResponseEntity<?> response = c.trigger(request, null, null)

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        (response.body as PipelineTriggerController.ErrorBody).error() == "Pipeline misconfigured for alias: ticket-scoring"
    }

    def "header tenant/correlation values win over body when both are supplied"() {
        given:
        PipelineExecutionHandle handle = new PipelineExecutionHandle("e", "embabel-pipe", Instant.now())
        String observedTenant = null
        String observedCorr = null
        gateway.trigger("embabel-pipe", _, _, _) >> { String pid, String body, String tid, String cid ->
            observedTenant = tid
            observedCorr = cid
            handle
        }
        PipelineTriggerRequest request = new PipelineTriggerRequest(
                "ticket-scoring", "body", "body-corr", "body-tenant", null)

        when:
        controller().trigger(request, "header-tenant", "header-corr")

        then:
        observedTenant == "header-tenant"
        observedCorr == "header-corr"
    }

    def "body tenant/correlation values are used when headers are absent"() {
        given:
        PipelineExecutionHandle handle = new PipelineExecutionHandle("e", "embabel-pipe", Instant.now())
        String observedTenant = null
        String observedCorr = null
        gateway.trigger("embabel-pipe", _, _, _) >> { String pid, String body, String tid, String cid ->
            observedTenant = tid
            observedCorr = cid
            handle
        }
        PipelineTriggerRequest request = new PipelineTriggerRequest(
                "ticket-scoring", "body", "body-corr", "body-tenant", null)

        when:
        controller().trigger(request, null, null)

        then:
        observedTenant == "body-tenant"
        observedCorr == "body-corr"
    }

    // ── GET /status/{executionId} ──────────────────────────────────────────

    def "status projects the tracked summary to the opaque StatusBody"() {
        given:
        Instant started = Instant.parse("2026-06-18T00:00:00Z")
        Instant completed = Instant.parse("2026-06-18T00:00:01Z")
        PipelineExecutionSummary summary = new PipelineExecutionSummary(
                "exec-1", "embabel-pipe", "tenant-a",
                started, completed, ExecutionStatus.SUCCESS, null,
                ["score": Duration.ofMillis(40)], null, Duration.ofSeconds(1))
        tracker.byId("exec-1") >> Optional.of(summary)

        when:
        ResponseEntity<?> response = controller().status("exec-1")

        then:
        response.statusCode == HttpStatus.OK
        StatusBody body = response.body as StatusBody
        body.id() == "exec-1"
        body.status() == "SUCCESS"
        body.startedAt() == started
        body.completedAt() == completed
        body.failureReason() == null
    }

    def "status returns 404 when the executionId is not tracked"() {
        given:
        tracker.byId("missing") >> Optional.empty()

        when:
        ResponseEntity<?> response = controller().status("missing")

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        (response.body as PipelineTriggerController.ErrorBody).error() == "No execution found for id: missing"
    }

    def "status returns 404 with a 'tracking disabled' diagnostic when the tracker bean is absent"() {
        given:
        ObjectProvider<PipelineExecutionTracker> noTrackerProvider = Mock {
            getIfAvailable() >> null
        }
        HttpTriggerProperties http = new HttpTriggerProperties(true, "/api/pipelines", ["a": "p"])
        PipelineProperties props = Mock {
            httpTrigger() >> http
        }
        PipelineTriggerController c = new PipelineTriggerController(gateway, props, noTrackerProvider)

        when:
        ResponseEntity<?> response = c.status("any-id")

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        (response.body as PipelineTriggerController.ErrorBody).error().contains("tracking is not enabled")
    }

    def "blank executionId returns 400"() {
        when:
        ResponseEntity<?> response = controller().status("  ")

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        (response.body as PipelineTriggerController.ErrorBody).error().contains("must not be blank")
    }

    // ── FAILED projection ──────────────────────────────────────────────────

    def "FAILED status surfaces the failure reason"() {
        given:
        Instant started = Instant.parse("2026-06-18T00:00:00Z")
        PipelineExecutionSummary summary = new PipelineExecutionSummary(
                "fail-1", "embabel-pipe", null,
                started, started.plusSeconds(1), ExecutionStatus.FAILED, "score",
                [:], "score stage threw NPE", Duration.ofSeconds(1))
        tracker.byId("fail-1") >> Optional.of(summary)

        when:
        ResponseEntity<?> response = controller().status("fail-1")

        then:
        response.statusCode == HttpStatus.OK
        StatusBody body = response.body as StatusBody
        body.status() == "FAILED"
        body.failureReason() == "score stage threw NPE"
    }
}
