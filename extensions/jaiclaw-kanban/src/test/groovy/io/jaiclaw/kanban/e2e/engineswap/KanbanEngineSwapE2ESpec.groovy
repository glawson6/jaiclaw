package io.jaiclaw.kanban.e2e.engineswap

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.kanban.service.TaskTransitionService
import io.jaiclaw.kanban.state.TaskStateEngine
import io.jaiclaw.tasks.JsonFileTaskStore
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

/**
 * Engine-swap E2E (plan §5.2 Phase 3 row, third bullet): flip
 * {@code jaiclaw.kanban.engine.name=spring-statemachine} and confirm
 * the same fixture board runs the same happy-path transition chain
 * through the SSM-backed engine.
 *
 * <p>Single test method — the unit-level {@code SpringStateMachineEngineSpec}
 * already pins per-event accept/reject parity with the graph engine;
 * this spec only proves the autoconfig wiring picks up the SSM bean
 * over the default graph bean when the property is set.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = [KanbanEngineSwapE2ESpec.TestApp])
@TestPropertySource(properties = [
        "jaiclaw.kanban.enabled=true",
        "jaiclaw.kanban.http.enabled=false",
        "jaiclaw.kanban.actuator.enabled=false",
        "jaiclaw.kanban.sse.enabled=false",
        "jaiclaw.kanban.processors.enabled=false",
        "jaiclaw.kanban.recovery.enabled=false",
        "jaiclaw.kanban.engine.name=spring-statemachine",
        "jaiclaw.kanban.locations.patterns=classpath:boards/e2e-content-review.yaml",
])
class KanbanEngineSwapE2ESpec extends Specification {

    static final Path tempDir = Files.createTempDirectory("kanban-engine-swap-e2e")

    @DynamicPropertySource
    static void registerStorageDirs(DynamicPropertyRegistry registry) {
        registry.add("jaiclaw.kanban.boards-dir", { tempDir.resolve("boards").toString() })
    }

    @Autowired TaskTransitionService transitionService
    @Autowired TaskStateEngine engine
    @Autowired TaskStore taskStore

    def "with engine.name=spring-statemachine the SSM-backed engine is wired in and walks the fixture chain"() {
        expect: "the SSM engine bean replaces the default graph engine"
        engine.class.simpleName == "SpringStateMachineEngine"

        when: "the same backlog → drafting → review → done chain that the graph engine walks"
        def card = transitionService.createCard("e2e-content-review", "Swap test", null, [:])
        def t1 = transitionService.transition(card.id(), "START", "ssm")
        def t2 = transitionService.transition(card.id(), "SUBMIT", "ssm")
        def t3 = transitionService.transition(card.id(), "APPROVE", "ssm")

        then:
        t1.accepted() && t1.toState() == "drafting"
        t2.accepted() && t2.toState() == "review"
        t3.accepted() && t3.toState() == "done"
        taskStore.findById(card.id()).get().state() == "done"
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        TaskStore swapSpecTaskStore() {
            Path dir = Files.createTempDirectory("kanban-swap-task-store")
            new JsonFileTaskStore(dir, new TenantGuard(TenantProperties.DEFAULT))
        }
    }
}
