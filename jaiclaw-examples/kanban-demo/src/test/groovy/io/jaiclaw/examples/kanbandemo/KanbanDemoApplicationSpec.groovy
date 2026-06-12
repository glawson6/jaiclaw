package io.jaiclaw.examples.kanbandemo

import io.jaiclaw.kanban.service.KanbanBoardService
import io.jaiclaw.tasks.TaskRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.util.function.Function

/**
 * Smoke spec — proves the kanban-demo's Spring context loads with all
 * the expected beans wired, and the fixture board is pre-registered.
 * The full end-to-end run (REST + SSE + ASCII + lifecycle) lives in the
 * .claude/skills/kanban-e2e skill which drives the real fat-jar
 * out-of-process.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = [
        "jaiclaw.kanban.boards-dir=\${java.io.tmpdir}/kanban-demo-spec/boards",
        "jaiclaw.tasks.demo.dir=\${java.io.tmpdir}/kanban-demo-spec/tasks",
])
class KanbanDemoApplicationSpec extends Specification {

    @Autowired
    KanbanBoardService boardService

    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("kanbanAgentRunner")
    Function<TaskRecord, String> runner

    def "context loads and the fixture demo board is pre-registered"() {
        expect:
        boardService.listAllUnscoped()*.id().contains("demo")
    }

    def "kanbanAgentRunner bean is provided"() {
        expect:
        runner != null
    }

    def "stub runner returns the expected deterministic prefix"() {
        given:
        def card = new TaskRecord("t1", "Q3 Blog Post", null,
                io.jaiclaw.tasks.TaskStatus.RUNNING,
                io.jaiclaw.tasks.TaskDeliveryState.PENDING,
                null, null, null, [:], java.time.Instant.now(), null, null,
                "default", "demo", "drafting", null, 0L, 0, null)

        expect:
        runner.apply(card) == "DRAFT: Q3 Blog Post"
    }
}
