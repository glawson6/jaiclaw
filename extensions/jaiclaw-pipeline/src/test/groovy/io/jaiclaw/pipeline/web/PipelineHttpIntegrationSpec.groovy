package io.jaiclaw.pipeline.web

import io.jaiclaw.pipeline.dsl.JaiClawPipeline
import io.jaiclaw.pipeline.gateway.PipelineExecutionHandle
import io.jaiclaw.pipeline.tracking.ExecutionStatus
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.util.function.Function

/**
 * Full-stack integration spec that boots the pipeline auto-configs alongside
 * Spring Web + Spring Boot Actuator and exercises the HTTP surfaces end-to-end
 * — the same paths the e2e runner curls externally. Catches regressions in
 * the auto-config wiring (the unit specs all mock that layer).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [PipelineHttpIntegrationSpec.TestApp]
)
@TestPropertySource(properties = [
        "management.endpoints.web.exposure.include=pipelines,health",
        "jaiclaw.skills.allow-bundled=",
        "jaiclaw.security.mode=none",
        "jaiclaw.pipeline.enabled=true"
])
class PipelineHttpIntegrationSpec extends Specification {

    @LocalServerPort
    int port

    @Autowired
    TestRestTemplate http

    @Autowired
    PipelineExecutionTracker tracker

    def "POST trigger returns 202 + handle, tracker records SUCCESS, actuator surfaces the execution"() {
        when: "submit via the controller"
        ResponseEntity<Map> trigger = http.postForEntity(
                "http://localhost:${port}/api/pipelines/processor-pipe/trigger",
                "hello integration",
                Map.class)

        then: "controller returns 202 with a handle"
        trigger.statusCode == HttpStatus.ACCEPTED
        trigger.body.executionId != null
        trigger.body.pipelineId == "processor-pipe"

        when: "the async pipeline finishes"
        String executionId = trigger.body.executionId as String
        ExecutionStatus status = pollStatus(executionId)

        then:
        status == ExecutionStatus.SUCCESS
        tracker.byId(executionId).get().stageDurations().containsKey("upper")
        tracker.byId(executionId).get().stageDurations().containsKey("exclaim")

        when: "the actuator list endpoint is hit"
        ResponseEntity<Map> list = http.getForEntity(
                "http://localhost:${port}/actuator/pipelines", Map.class)

        then:
        list.statusCode == HttpStatus.OK
        list.body.count as int >= 1
        list.body.pipelines.any { it.id == "processor-pipe" }

        when: "the actuator byId endpoint is hit"
        ResponseEntity<Map> byId = http.getForEntity(
                "http://localhost:${port}/actuator/pipelines/processor-pipe", Map.class)

        then:
        byId.statusCode == HttpStatus.OK
        byId.body.definition.id == "processor-pipe"
        byId.body.recentExecutions.any { it.executionId == executionId }
    }

    def "POST trigger for unknown pipeline returns 404 with error body"() {
        when:
        ResponseEntity<Map> response = http.postForEntity(
                "http://localhost:${port}/api/pipelines/does-not-exist/trigger",
                "x", Map.class)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.error != null
        (response.body.error as String).contains("does-not-exist")
    }

    /** Poll the tracker every 50 ms (up to 5 s) until the execution leaves RUNNING. */
    private ExecutionStatus pollStatus(String executionId) {
        long deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            def summary = tracker.byId(executionId).orElse(null)
            if (summary != null && summary.status() != ExecutionStatus.RUNNING) {
                return summary.status()
            }
            Thread.sleep(50)
        }
        return ExecutionStatus.RUNNING
    }

    // ---- self-contained test app ----

    @SpringBootApplication
    @Configuration
    static class TestApp {
        @Bean
        Function<String, String> upperCase() { return { String s -> s == null ? "" : s.toUpperCase() } }

        @Bean
        Function<String, String> addExclaim() { return { String s -> (s == null ? "" : s) + "!" } }

        @Bean
        JaiClawPipeline testPipelines() {
            return new JaiClawPipeline() {
                @Override
                void define() {
                    pipeline("processor-pipe")
                            .name("Integration Test Pipeline")
                            .trigger().manual()
                            .then("upper").processor("upperCase")
                            .then("exclaim").processor("addExclaim")
                            .output().log().template("upper={{stages.upper.output}} input-was={{input}}")
                }
            }
        }
    }
}
