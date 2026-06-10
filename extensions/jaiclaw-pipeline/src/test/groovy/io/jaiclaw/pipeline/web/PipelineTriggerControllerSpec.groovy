package io.jaiclaw.pipeline.web

import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle
import io.jaiclaw.pipeline.gateway.PipelineGateway
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification

import java.time.Instant

class PipelineTriggerControllerSpec extends Specification {

    PipelineGateway gateway = Mock()
    PipelineTriggerController controller = new PipelineTriggerController(gateway)

    def "trigger returns 202 with handle body"() {
        given:
        PipelineExecutionHandle handle = new PipelineExecutionHandle("exec-1", "p1", Instant.now())
        gateway.trigger("p1", "hello", "tenant-a", "corr-z") >> handle

        when:
        ResponseEntity<?> response = controller.trigger("p1", "hello", "tenant-a", "corr-z")

        then:
        response.statusCode == HttpStatus.ACCEPTED
        response.body == handle
    }

    def "trigger returns 404 with error body for unknown pipeline"() {
        given:
        gateway.trigger("nope", _ as String, _, _) >> {
            throw new IllegalArgumentException("Unknown pipeline: 'nope'")
        }

        when:
        ResponseEntity<?> response = controller.trigger("nope", "body", null, null)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        (response.body as PipelineTriggerController.ErrorBody).error() == "Unknown pipeline: 'nope'"
    }

    def "trigger forwards null tenantId and correlationId headers"() {
        given:
        PipelineExecutionHandle handle = new PipelineExecutionHandle("e", "p", Instant.now())
        String observedTenant = "before"
        String observedCorrelation = "before"
        gateway.trigger("p", "body", _, _) >> { String pid, String body, String tid, String cid ->
            observedTenant = tid
            observedCorrelation = cid
            handle
        }

        when:
        controller.trigger("p", "body", null, null)

        then:
        observedTenant == null
        observedCorrelation == null
    }
}
