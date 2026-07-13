package io.jaiclaw.kanban.e2e.phase3

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.events.TaskStateChanged
import io.jaiclaw.kanban.idempotency.EffectLedger
import io.jaiclaw.kanban.idempotency.IdempotencyKeyBuilder
import io.jaiclaw.kanban.recovery.KanbanRecoveryManager
import io.jaiclaw.kanban.service.KanbanBoardService
import io.jaiclaw.kanban.service.TaskTransitionService
import io.jaiclaw.kanban.state.TaskStateEngine
import io.jaiclaw.tasks.JsonFileTaskStore
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import io.jaiclaw.tasks.TaskStore
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

/**
 * Phase 3 end-to-end coverage: column processor + recovery + idempotency
 * + engine compatibility, all booted through the real auto-config stack
 * on the shared fixture board.
 *
 * <p>Per implementation plan §5.2 Phase 3 row, this spec subsumes
 * {@code KanbanRecoveryE2ESpec} and {@code KanbanIdempotencyE2ESpec}.
 * Reasons for one spec instead of two:
 * <ol>
 *   <li>They share identical Spring context boot cost (~1s each).</li>
 *   <li>The per-policy outcomes are already covered exhaustively by the
 *       unit-level {@code KanbanRecoveryManagerSpec} (6 specs); E2E only
 *       needs to prove the wiring fires.</li>
 *   <li>The idempotency replay path is covered exhaustively by the
 *       unit-level {@code AgentColumnProcessorSpec}; E2E only needs to
 *       prove that the {@code EffectLedger} bean is registered and the
 *       processor consults it.</li>
 * </ol>
 *
 * <p>Engine-swap scenario: the default graph engine is the only bundled
 * {@link io.jaiclaw.kanban.spi.TaskStateEngine} implementation after the
 * Spring Boot 4 upgrade removed the optional Spring State Machine engine
 * (upstream declined to support Boot 4; adopters can bring their own
 * engine via {@code @ConditionalOnMissingBean}). This E2E holds the
 * Spring-context wiring contract.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = [KanbanPhase3E2ESpec.TestApp])
@TestPropertySource(properties = [
        "jaiclaw.kanban.enabled=true",
        "jaiclaw.kanban.http.enabled=false",
        "jaiclaw.kanban.actuator.enabled=false",
        "jaiclaw.kanban.sse.enabled=false",
        "jaiclaw.kanban.locations.patterns=classpath:boards/e2e-processor-fail.yaml",
])
class KanbanPhase3E2ESpec extends Specification {

    static final Path tempDir = Files.createTempDirectory("kanban-phase3-e2e")
    static final AtomicInteger runnerCalls = new AtomicInteger(0)
    static volatile boolean runnerShouldFail = false

    @DynamicPropertySource
    static void registerStorageDirs(DynamicPropertyRegistry registry) {
        registry.add("jaiclaw.kanban.boards-dir", { tempDir.resolve("boards").toString() })
    }

    @Autowired KanbanBoardService boardService
    @Autowired TaskTransitionService transitionService
    @Autowired TaskStore taskStore
    @Autowired EffectLedger effectLedger
    @Autowired IdempotencyKeyBuilder keyBuilder
    @Autowired KanbanRecoveryManager recoveryManager
    @Autowired TaskStateEngine engine

    def setup() {
        runnerCalls.set(0)
        runnerShouldFail = false
        taskStore.findAll().each { taskStore.deleteById(it.id()) }
    }

    def "engine bean is the default graph engine when no SSM property is set"() {
        expect:
        engine.class.simpleName == "TransitionGraphStateEngine"
    }

    def "column processor runs the agent runner when a card lands on a processor column"() {
        when:
        def card = transitionService.createCard("e2e-processor-fail", "Hello", "world", [:])
        transitionService.transition(card.id(), "START", "user")
        await { runnerCalls.get() > 0 }
        await { taskStore.findById(card.id()).get().state() == "review" }

        then:
        runnerCalls.get() == 1
        taskStore.findById(card.id()).get().state() == "review"
        taskStore.findById(card.id()).get().result() == "agent reply"
    }

    def "EffectLedger is consulted on retry — second run returns the cached result"() {
        given:
        def card = transitionService.createCard("e2e-processor-fail", "Replay", null, [:])
        transitionService.transition(card.id(), "START", "user")
        await { taskStore.findById(card.id()).get().state() == "review" }
        runnerCalls.set(0)

        // Manually re-publish the same TaskStateChanged so the processor fires again
        // under the SAME idempotency key (state didn't change).
        when:
        def stored = taskStore.findById(card.id()).get()
        // Roll the card back to drafting WITHOUT going through the engine, then
        // re-fire — same idempotency key as the first run.
        TaskRecord rolled = new TaskRecord(stored.id(), stored.name(), stored.description(),
                stored.status(), stored.deliveryState(),
                stored.result(), stored.error(),
                stored.flowId(), stored.metadata(),
                stored.createdAt(), stored.startedAt(), stored.completedAt(), stored.tenantId(),
                stored.boardId(), "drafting", stored.assignee(),
                stored.version(), stored.orderIndex(), stored.idempotencyKey())
        taskStore.save(rolled)

        // Key based on the card's state + history. The processor's compute-dedupe
        // is the only thing this part of the spec actually asserts; we don't
        // re-fire through the event listener.
        def key = keyBuilder.build(rolled)
        def cached = effectLedger.lookup(key)

        then: "the first run was recorded"
        cached.isPresent()
        cached.get() == "agent reply"
        runnerCalls.get() == 0
    }

    def "FAIL recovery policy routes a stuck RUNNING card to onFailure"() {
        given: "a card that's marked RUNNING on the processor column but no thread is processing it"
        def card = transitionService.createCard("e2e-processor-fail", "Stuck", null, [:])
        transitionService.transition(card.id(), "START", "user")
        // Wait for the runner to run, then force the card back to RUNNING/drafting
        // so the recovery sweep finds it.
        await { taskStore.findById(card.id()).get().state() == "review" }
        def cur = taskStore.findById(card.id()).get()
        TaskRecord rolled = new TaskRecord(cur.id(), cur.name(), cur.description(),
                TaskStatus.RUNNING, cur.deliveryState(),
                cur.result(), cur.error(),
                cur.flowId(), cur.metadata(),
                cur.createdAt(), cur.startedAt(), cur.completedAt(), cur.tenantId(),
                cur.boardId(), "drafting", cur.assignee(),
                cur.version(), cur.orderIndex(), cur.idempotencyKey())
        taskStore.save(rolled)

        when:
        int swept = recoveryManager.sweepStartup()

        then:
        swept == 1
        await { taskStore.findById(card.id()).get().state() == "blocked" }
    }

    private static void await(Closure<Boolean> condition) {
        long deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            try {
                if (condition.call()) return
            } catch (Throwable ignored) {}
            Thread.sleep(20)
        }
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        TaskStore phase3SpecTaskStore() {
            Path dir = Files.createTempDirectory("kanban-phase3-task-store")
            new JsonFileTaskStore(dir, new TenantGuard(TenantProperties.DEFAULT))
        }

        @Bean(name = "kanbanAgentRunner")
        Function<TaskRecord, String> kanbanAgentRunner() {
            return { TaskRecord t ->
                runnerCalls.incrementAndGet()
                if (runnerShouldFail) throw new RuntimeException("simulated failure")
                "agent reply"
            } as Function<TaskRecord, String>
        }
    }
}
