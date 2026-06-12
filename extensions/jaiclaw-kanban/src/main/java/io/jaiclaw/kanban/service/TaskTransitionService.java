package io.jaiclaw.kanban.service;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.kanban.events.KanbanHookFirer;
import io.jaiclaw.kanban.events.TaskStateChanged;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.ColumnDefinition;
import io.jaiclaw.kanban.model.TransitionRecord;
import io.jaiclaw.kanban.state.TaskStateEngine;
import io.jaiclaw.kanban.state.TransitionResult;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Orchestrates a card transition end-to-end: loads the {@link TaskRecord},
 * runs the {@link TaskStateEngine}, persists via
 * {@link TaskStore#compareAndSave}, records the transition in
 * {@link TransitionHistory}, publishes the Spring {@link TaskStateChanged}
 * event, and fires {@link KanbanHookFirer}.
 *
 * <p>One {@link ReentrantLock} per task id serialises concurrent transitions
 * on the same card within this JVM (analysis §6.6 Phase-1 race guard). The
 * lock map is bounded only by the universe of in-flight task ids; entries
 * are removed when locks become unused, see {@link #unlockAndCleanup}.
 *
 * <p>Cross-instance race protection comes from the optimistic
 * {@code TaskRecord.version}: a second instance whose {@code compareAndSave}
 * fails re-reads and re-validates, which is the correct behaviour per
 * analysis §6.6.
 *
 * <p>Phase 2 SSE consumes the published Spring event; the payload shape is
 * frozen here per the plan's SPI freeze marker.
 */
public class TaskTransitionService {

    private static final Logger log = LoggerFactory.getLogger(TaskTransitionService.class);

    private final TaskStore taskStore;
    private final KanbanBoardService boardService;
    private final TaskStateEngine engine;
    private final TransitionHistory history;
    private final ApplicationEventPublisher publisher;
    private final KanbanHookFirer hookFirer;
    private final TenantGuard tenantGuard;
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public TaskTransitionService(
            TaskStore taskStore,
            KanbanBoardService boardService,
            TaskStateEngine engine,
            TransitionHistory history,
            ApplicationEventPublisher publisher,
            KanbanHookFirer hookFirer,
            TenantGuard tenantGuard) {
        this.taskStore = taskStore;
        this.boardService = boardService;
        this.engine = engine;
        this.history = history;
        this.publisher = publisher;
        this.hookFirer = hookFirer;
        this.tenantGuard = tenantGuard;
    }

    /**
     * Create a new card on the given board and place it in the board's
     * initial state. Tenant is resolved from the current
     * {@code TenantContext}.
     */
    public TaskRecord createCard(String boardId, String name, String description,
                                 Map<String, String> metadata) {
        BoardDefinition board = boardService.get(boardId)
                .orElseThrow(() -> new IllegalArgumentException("board not found: " + boardId));
        String tenantId = resolveTenantId();
        TaskRecord card = new TaskRecord(
                UUID.randomUUID().toString(),
                name,
                description,
                board.column(board.initialState()).map(ColumnDefinition::phase).orElse(null),
                null,
                null, null, null,
                metadata != null ? Map.copyOf(metadata) : Map.of(),
                Instant.now(),
                null, null,
                tenantId,
                boardId,
                board.initialState(),
                null,
                0L,
                0,
                null);
        taskStore.save(card);
        // Emit a "created" transition so dashboards see the card appear in the initial column.
        TransitionRecord rec = new TransitionRecord(
                card.id(), boardId, null, board.initialState(),
                "CREATE", null, tenantId, Instant.now());
        history.record(rec);
        publisher.publishEvent(new TaskStateChanged(rec, card));
        hookFirer.fireTransition(rec);
        return card;
    }

    /**
     * Try to fire {@code event} on the card identified by {@code taskId}.
     * Returns the {@link TransitionResult} from the engine. On accepted
     * transitions, the card is persisted via
     * {@link TaskStore#compareAndSave} (so a stale write under contention
     * yields a {@code 409} at the REST layer).
     */
    public TransitionResult transition(String taskId, String event, String actor) {
        ReentrantLock lock = locks.computeIfAbsent(taskId, k -> new ReentrantLock());
        lock.lock();
        try {
            TaskRecord task = taskStore.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("task not found: " + taskId));
            if (task.boardId() == null) {
                return TransitionResult.reject(task.state(),
                        "task " + taskId + " is not attached to a board");
            }
            BoardDefinition board = boardService.get(task.boardId())
                    .orElseThrow(() -> new IllegalStateException(
                            "task references unknown board: " + task.boardId()));
            Map<String, Object> ctx = buildContext(board, task, event);
            TransitionResult result = engine.fire(board, task, event, ctx);
            if (!result.accepted()) {
                return result;
            }
            ColumnDefinition target = board.column(result.toState())
                    .orElseThrow(() -> new IllegalStateException(
                            "engine accepted but target column missing: " + result.toState()));
            TaskRecord moved = task
                    .withState(result.toState())
                    .withStatus(target.phase());
            Optional<TaskRecord> persisted = taskStore.compareAndSave(moved);
            if (persisted.isEmpty()) {
                log.debug("compareAndSave conflict on task {}; caller should re-read", taskId);
                return TransitionResult.reject(result.fromState(),
                        "concurrent modification — re-read and retry");
            }
            TransitionRecord rec = new TransitionRecord(
                    taskId, board.id(), result.fromState(), result.toState(),
                    event, actor, task.tenantId(), Instant.now());
            history.record(rec);
            publisher.publishEvent(new TaskStateChanged(rec, persisted.get()));
            hookFirer.fireTransition(rec);
            return result;
        } finally {
            unlockAndCleanup(taskId, lock);
        }
    }

    public List<String> allowedEvents(String taskId) {
        TaskRecord task = taskStore.findById(taskId).orElse(null);
        if (task == null || task.boardId() == null) return List.of();
        BoardDefinition board = boardService.get(task.boardId()).orElse(null);
        if (board == null) return List.of();
        return engine.allowedEvents(board, task.state());
    }

    private Map<String, Object> buildContext(BoardDefinition board, TaskRecord task, String event) {
        Map<String, Object> ctx = new HashMap<>();
        board.transitions().stream()
                .filter(t -> t.from().equals(task.state()) && t.event().equals(event))
                .findFirst()
                .ifPresent(t -> {
                    int currentCount = taskStore.findByBoardAndState(board.id(), t.to()).size();
                    ctx.put("wipCount", currentCount);
                });
        return ctx;
    }

    private String resolveTenantId() {
        if (tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantGuard.getProperties().defaultTenantId();
    }

    private void unlockAndCleanup(String taskId, ReentrantLock lock) {
        lock.unlock();
        // Best-effort eviction so the lock map doesn't grow unbounded; a racing
        // thread that just acquired the same lock keeps it alive in the map.
        if (!lock.isHeldByCurrentThread() && !lock.hasQueuedThreads()) {
            locks.remove(taskId, lock);
        }
    }
}
