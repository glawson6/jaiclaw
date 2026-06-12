package io.jaiclaw.kanban.engine;

import io.jaiclaw.kanban.events.TaskStateChanged;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Subscribes to {@link TaskStateChanged} and, when a card lands in a
 * column that declares a processor, runs the processor on a virtual
 * thread. On handler return fires {@code processor.onSuccess} into the
 * state engine; on handler exception fires {@code processor.onFailure}.
 *
 * <p>Design constraints:
 * <ul>
 *   <li>The manager never directly invokes {@code AgentRuntime} —
 *       analysis §3.6 is explicit that kanban stays agent-runtime-agnostic.
 *       The wiring app supplies the {@code Function<TaskRecord, String>}
 *       runner that's injected into {@link AgentColumnProcessor}.</li>
 *   <li>We deliberately do <b>not</b> use {@code TaskExecutor.submit}:
 *       {@code TaskExecutor} flips status to {@code RUNNING} and on
 *       success writes {@code task.withResult(...)} which clobbers the
 *       {@code state} field a concurrent transition may have advanced.
 *       The processor needs the card to remain in its column while it
 *       runs, then move via the state engine — that lifecycle is
 *       genuinely different from the standalone task one, so the
 *       manager owns its own virtual-thread executor.</li>
 *   <li>Reentrancy: an {@code onSuccess} event fired from the handler
 *       thread synchronously re-enters this listener through Spring's
 *       event dispatch. If the next column also has a processor we
 *       submit again — that's the pipeline-of-processors behaviour.
 *       Self-cycling is prevented by the board's transition graph
 *       (terminal columns have no outgoing edges).</li>
 * </ul>
 */
public class ColumnProcessorManager {

    private static final Logger log = LoggerFactory.getLogger(ColumnProcessorManager.class);

    private final KanbanBoardService boardService;
    private final TaskStore taskStore;
    private final TaskTransitionService transitionService;
    private final AgentColumnProcessor agentProcessor;
    private final Executor executor;

    public ColumnProcessorManager(KanbanBoardService boardService,
                                  TaskStore taskStore,
                                  TaskTransitionService transitionService,
                                  AgentColumnProcessor agentProcessor) {
        this(boardService, taskStore, transitionService, agentProcessor,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    /** Package-private constructor for tests that need a synchronous executor. */
    ColumnProcessorManager(KanbanBoardService boardService,
                           TaskStore taskStore,
                           TaskTransitionService transitionService,
                           AgentColumnProcessor agentProcessor,
                           Executor executor) {
        this.boardService = boardService;
        this.taskStore = taskStore;
        this.transitionService = transitionService;
        this.agentProcessor = agentProcessor;
        this.executor = executor;
    }

    @EventListener
    public void onTaskStateChanged(TaskStateChanged event) {
        if (event.toState() == null) return;
        BoardDefinition board = boardService.get(event.boardId()).orElse(null);
        if (board == null) return;
        ColumnPolicy policy = board.column(event.toState())
                .map(ColumnPolicy::of)
                .orElse(null);
        if (policy == null || !policy.hasProcessor()) return;
        if (!"agent".equalsIgnoreCase(policy.processorType())) {
            log.warn("Unsupported processor type '{}' on column {}/{} — skipping",
                    policy.processorType(), event.boardId(), event.toState());
            return;
        }

        TaskRecord card = event.task();
        executor.execute(() -> runProcessor(card, policy));
    }

    private void runProcessor(TaskRecord card, ColumnPolicy policy) {
        try {
            String result = agentProcessor.process(card, policy);
            persistResult(card.id(), result);
            fireOnSuccess(policy, card);
        } catch (RuntimeException ex) {
            log.warn("Processor failed for card {} on {}: {}",
                    card.id(), policy.state(), ex.getMessage());
            persistError(card.id(), ex.getMessage());
            fireOnFailure(policy, card);
        }
    }

    /** Write the result string without touching state, status, or version. */
    private void persistResult(String taskId, String result) {
        Optional<TaskRecord> current = taskStore.findById(taskId);
        if (current.isEmpty()) return;
        TaskRecord updated = current.get();
        TaskRecord withResult = new TaskRecord(updated.id(), updated.name(), updated.description(),
                updated.status(), updated.deliveryState(),
                result == null ? "" : result, null,
                updated.flowId(), updated.metadata(),
                updated.createdAt(), updated.startedAt(), updated.completedAt(), updated.tenantId(),
                updated.boardId(), updated.state(), updated.assignee(),
                updated.version(), updated.orderIndex(), updated.idempotencyKey());
        taskStore.save(withResult);
    }

    private void persistError(String taskId, String message) {
        Optional<TaskRecord> current = taskStore.findById(taskId);
        if (current.isEmpty()) return;
        TaskRecord updated = current.get();
        TaskRecord withError = new TaskRecord(updated.id(), updated.name(), updated.description(),
                updated.status(), updated.deliveryState(),
                updated.result(), message,
                updated.flowId(), updated.metadata(),
                updated.createdAt(), updated.startedAt(), updated.completedAt(), updated.tenantId(),
                updated.boardId(), updated.state(), updated.assignee(),
                updated.version(), updated.orderIndex(), updated.idempotencyKey());
        taskStore.save(withError);
    }

    private void fireOnSuccess(ColumnPolicy policy, TaskRecord card) {
        if (policy.onSuccessEvent() == null || policy.onSuccessEvent().isBlank()) return;
        if (taskStore.findById(card.id()).isEmpty()) return;
        transitionService.transition(card.id(), policy.onSuccessEvent(), "processor");
    }

    private void fireOnFailure(ColumnPolicy policy, TaskRecord card) {
        if (policy.onFailureEvent() == null || policy.onFailureEvent().isBlank()) return;
        if (taskStore.findById(card.id()).isEmpty()) return;
        try {
            transitionService.transition(card.id(), policy.onFailureEvent(), "processor");
        } catch (RuntimeException routingFailure) {
            log.warn("onFailure routing also failed for {}: {}",
                    card.id(), routingFailure.getMessage());
        }
    }
}
