package io.jaiclaw.pipeline.web

import io.jaiclaw.core.hook.event.PipelineExecutionStartedEvent
import io.jaiclaw.core.hook.event.PipelineStageCompletedEvent
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.pipeline.PipelineProperties
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import spock.lang.Specification

class PipelineEventBroadcasterSpec extends Specification {

    PipelineRegistry registry = Mock()
    PipelineExecutionTracker tracker = Mock()
    PipelineProperties.SseProperties sseProps =
            new PipelineProperties.SseProperties(true, 15, 100, 300, false)  // includeSnapshot=false — keeps register() side-effect-free

    // No TenantGuard — broadcaster falls back to "default" tenant id via defaultTenantId()
    PipelineEventBroadcaster broadcaster =
            new PipelineEventBroadcaster(registry, tracker, null, sseProps)

    def cleanup() {
        TenantContextHolder.clear()
    }

    def "register accepts an emitter and returns it"() {
        given:
        SseEmitter emitter = new SseEmitter(60_000L)

        when:
        SseEmitter result = broadcaster.register("pipe-A", emitter)

        then:
        result === emitter
        broadcaster.totalConnections() == 1
    }

    def "register rejects when tenant cap is exceeded"() {
        given:
        PipelineProperties.SseProperties tight =
                new PipelineProperties.SseProperties(true, 15, 1, 300, false)
        PipelineEventBroadcaster bcast =
                new PipelineEventBroadcaster(registry, tracker, null, tight)

        when:
        SseEmitter first = bcast.register("pipe-A", new SseEmitter(60_000L))
        SseEmitter second = bcast.register("pipe-A", new SseEmitter(60_000L))

        then:
        first != null
        second == null   // over cap
    }

    def "execution-started event fans out to the specific-pipeline emitter"() {
        given:
        SseEmitter emitter = Spy(new SseEmitter(60_000L))
        broadcaster.register("pipe-A", emitter)
        def event = PipelineExecutionStartedEvent.of("pipe-A", "exec-1", null)

        when:
        broadcaster.onExecutionStarted(event)

        then:
        // The broadcaster calls emitter.send(SseEventBuilder) once per matching emitter.
        // We just verify the invocation happened — the internal serialization is exercised
        // by the SSE integration path (PipelineHttpIntegrationSpec).
        (1.._) * emitter.send(_)
    }

    def "event to pipe-A does NOT reach an emitter registered for pipe-B"() {
        given:
        SseEmitter emitterA = Spy(new SseEmitter(60_000L))
        SseEmitter emitterB = Spy(new SseEmitter(60_000L))
        broadcaster.register("pipe-A", emitterA)
        broadcaster.register("pipe-B", emitterB)
        def event = PipelineExecutionStartedEvent.of("pipe-A", "exec-1", null)

        when:
        broadcaster.onExecutionStarted(event)

        then:
        (1.._) * emitterA.send(_)
        0 * emitterB.send(_)
    }

    def "GLOBAL emitter receives events for every pipeline in its tenant"() {
        given:
        SseEmitter global = Spy(new SseEmitter(60_000L))
        broadcaster.register(PipelineEventBroadcaster.GLOBAL_PIPELINE_ID, global)

        when:
        broadcaster.onExecutionStarted(PipelineExecutionStartedEvent.of("pipe-A", "exec-1", null))
        broadcaster.onExecutionStarted(PipelineExecutionStartedEvent.of("pipe-B", "exec-2", null))
        broadcaster.onStageCompleted(PipelineStageCompletedEvent.of(
                "pipe-A", "exec-1", null, "s1", "PROCESSOR", 0, "hi", 5L))

        then:
        (3.._) * global.send(_)
    }

    def "totalConnections + connectionsForCurrentTenant reflect registrations"() {
        given:
        broadcaster.register("pipe-A", new SseEmitter(60_000L))
        broadcaster.register("pipe-A", new SseEmitter(60_000L))
        broadcaster.register(PipelineEventBroadcaster.GLOBAL_PIPELINE_ID, new SseEmitter(60_000L))

        expect:
        broadcaster.totalConnections() == 3
        broadcaster.connectionsForCurrentTenant() == 3
    }
}
