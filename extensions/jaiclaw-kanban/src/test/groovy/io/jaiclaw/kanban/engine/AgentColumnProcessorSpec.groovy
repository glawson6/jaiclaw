package io.jaiclaw.kanban.engine

import io.jaiclaw.kanban.idempotency.EffectLedger
import io.jaiclaw.kanban.idempotency.IdempotencyKeyBuilder
import io.jaiclaw.kanban.model.ColumnDefinition
import io.jaiclaw.kanban.model.ProcessorDefinition
import io.jaiclaw.kanban.model.TransitionRecord
import io.jaiclaw.kanban.service.TransitionHistory
import io.jaiclaw.tasks.TaskDeliveryState
import io.jaiclaw.tasks.TaskRecord
import io.jaiclaw.tasks.TaskStatus
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

class AgentColumnProcessorSpec extends Specification {

    @TempDir
    Path tempDir
    TransitionHistory history = new TransitionHistory(50)
    IdempotencyKeyBuilder keyBuilder = new IdempotencyKeyBuilder(history)
    EffectLedger ledger

    def setup() {
        ledger = new EffectLedger(tempDir)
        history.record(new TransitionRecord("t1", "b1", "backlog", "drafting",
                "START", null, "default", Instant.now()))
    }

    private TaskRecord card() {
        new TaskRecord("t1", "Card", "Original description",
                TaskStatus.RUNNING, TaskDeliveryState.PENDING,
                null, null, null, [:], Instant.now(), null, null, "default",
                "b1", "drafting", null, 0L, 0, null)
    }

    private ColumnPolicy policy(String template = "Draft: {{name}}/{{description}}/{{attempt}}/{{idempotencyKey}}") {
        def col = new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING, null, false, null,
                new ProcessorDefinition("agent", template, "review", "blocked",
                        true, "requeue", 3, [:]))
        ColumnPolicy.of(col)
    }

    def "process renders the template and calls the runner once"() {
        given:
        def calls = new AtomicInteger(0)
        Function<TaskRecord, String> runner = { TaskRecord t ->
            calls.incrementAndGet()
            // The runner sees the rendered prompt in description (per design)
            assert t.description() == "Draft: Card/Original description/1/b1:t1:drafting:1"
            "agent reply"
        }
        def processor = new AgentColumnProcessor(runner, keyBuilder, ledger)

        when:
        def result = processor.process(card(), policy())

        then:
        result == "agent reply"
        calls.get() == 1
        ledger.lookup("b1:t1:drafting:1").get() == "agent reply"
    }

    def "process replays from the ledger on retry without calling the runner"() {
        given:
        ledger.record("b1:t1:drafting:1", "cached")
        def calls = new AtomicInteger(0)
        Function<TaskRecord, String> runner = { TaskRecord t -> calls.incrementAndGet(); "fresh" }
        def processor = new AgentColumnProcessor(runner, keyBuilder, ledger)

        when:
        def result = processor.process(card(), policy())

        then:
        result == "cached"
        calls.get() == 0
    }

    def "process is a no-op when the column has no processor"() {
        given:
        def col = new ColumnDefinition("drafting", "Drafting", TaskStatus.RUNNING, null, false, null, null)
        def emptyPolicy = ColumnPolicy.of(col)
        Function<TaskRecord, String> runner = { TaskRecord t -> "unused" }
        def processor = new AgentColumnProcessor(runner, keyBuilder, ledger)

        when:
        def result = processor.process(card(), emptyPolicy)

        then:
        result == null
    }

    def "renderTemplate fills variables and ignores unknown ones"() {
        expect:
        AgentColumnProcessor.renderTemplate("hi {{name}}, attempt {{attempt}}, gone {{nope}}",
                [name: "Alice", attempt: "2"]) == "hi Alice, attempt 2, gone "
    }

    def "process exposes attempt count from metadata"() {
        given:
        def withAttempts = new TaskRecord("t1", "Card", null,
                TaskStatus.RUNNING, TaskDeliveryState.PENDING,
                null, null, null, [(AgentColumnProcessor.ATTEMPT_META_KEY): "3"],
                Instant.now(), null, null, "default",
                "b1", "drafting", null, 0L, 0, null)
        String seenPrompt = null
        Function<TaskRecord, String> runner = { TaskRecord t ->
            seenPrompt = t.description()
            "ok"
        }
        def processor = new AgentColumnProcessor(runner, keyBuilder, ledger)

        when:
        processor.process(withAttempts, policy())

        then:
        seenPrompt.contains("/3/")    // attempt=3 in the rendered prompt
    }
}
