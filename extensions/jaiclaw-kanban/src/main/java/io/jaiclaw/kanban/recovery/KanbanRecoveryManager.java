package io.jaiclaw.kanban.recovery;

import io.jaiclaw.kanban.KanbanProperties;
import io.jaiclaw.kanban.engine.ColumnPolicy;
import io.jaiclaw.kanban.engine.RestartPolicy;
import io.jaiclaw.kanban.events.TaskStateChanged;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.ColumnDefinition;
import io.jaiclaw.kanban.model.TransitionRecord;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStatus;
import io.jaiclaw.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Startup sweep + stale-execution policy enforcement for cards stuck on
 * processor columns. Implements the analysis §6.2 / plan §6.2 recovery
 * matrix:
 *
 * <ul>
 *   <li>{@code FAIL} — fires the column's {@code onFailure} event with the
 *       reason {@code "interrupted by restart"} (or
 *       {@code "stale-running timeout"}). Card lands in the configured
 *       failure column where it surfaces on the board.</li>
 *   <li>{@code REQUEUE} — increments
 *       {@code metadata["kanban.attempts"]}, persists, and re-publishes a
 *       {@link io.jaiclaw.kanban.events.TaskStateChanged} so
 *       {@link io.jaiclaw.kanban.engine.ColumnProcessorManager}
 *       resubmits. Capped by {@code maxAttempts}; on exhaustion falls
 *       through to {@code FAIL}.</li>
 *   <li>{@code MANUAL} — leaves the card in place but marks
 *       {@code metadata["kanban.interrupted"]=true} for operator-driven
 *       boards.</li>
 * </ul>
 *
 * <p>{@link SmartLifecycle} ensures the sweep runs after all the
 * required beans (TaskStore, KanbanBoardService, TaskTransitionService)
 * are wired and started.
 */
public class KanbanRecoveryManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(KanbanRecoveryManager.class);

    static final String ATTEMPT_META_KEY = "kanban.attempts";
    static final String INTERRUPTED_META_KEY = "kanban.interrupted";

    private final KanbanBoardService boardService;
    private final TaskStore taskStore;
    private final TaskTransitionService transitionService;
    private final KanbanProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private volatile boolean running = false;
    private boolean swept = false;

    public KanbanRecoveryManager(KanbanBoardService boardService,
                                 TaskStore taskStore,
                                 TaskTransitionService transitionService,
                                 KanbanProperties properties,
                                 ApplicationEventPublisher eventPublisher) {
        this.boardService = boardService;
        this.taskStore = taskStore;
        this.transitionService = transitionService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public synchronized void start() {
        if (running) return;
        running = true;
        if (!properties.recovery().enabled()) {
            log.info("Kanban recovery disabled — skipping startup sweep");
            return;
        }
        if (swept) return;
        swept = true;
        try {
            int recovered = sweepStartup();
            log.info("Kanban startup recovery: examined {} cards on processor columns", recovered);
        } catch (RuntimeException ex) {
            log.warn("Kanban startup recovery failed: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Run AFTER stores & transition service start, BEFORE the broadcaster
        // begins streaming SSE.
        return Integer.MAX_VALUE - 300;
    }

    /** Visible to {@link StaleRunningDetector}. */
    public int sweepStale(Duration staleTimeout) {
        if (!properties.recovery().enabled()) return 0;
        return sweep(card -> {
            if (card.startedAt() == null) return false;
            return Duration.between(card.startedAt(), Instant.now()).compareTo(staleTimeout) > 0;
        }, "stale-running timeout");
    }

    /** Returns the count of cards examined. */
    int sweepStartup() {
        return sweep(card -> true, "interrupted by restart");
    }

    private int sweep(java.util.function.Predicate<TaskRecord> selector, String reason) {
        Map<String, ColumnPolicy> processorColumns = collectProcessorColumns();
        if (processorColumns.isEmpty()) return 0;
        int[] examined = {0};
        sweepEachStore((card) -> {
            if (card.boardId() == null || card.state() == null) return;
            if (card.status() != TaskStatus.RUNNING && card.status() != TaskStatus.QUEUED) return;
            ColumnPolicy policy = processorColumns.get(card.boardId() + ":" + card.state());
            if (policy == null) return;
            if (!selector.test(card)) return;
            examined[0]++;
            applyPolicy(card, policy, reason);
        });
        return examined[0];
    }

    /**
     * Apply {@code consumer} to every card across every backend visible
     * to this recovery manager. Plan §9 / analysis §6.7: when
     * {@code taskStore} is a
     * {@link io.jaiclaw.tasks.persistence.TenantRoutingTaskStore}, the
     * sweep must iterate {@code (tenantId, store)} pairs with the tenant
     * context set on each pass — otherwise only the default store's
     * cards are visible (no tenant context at boot ⇒ router falls
     * through to default).
     */
    private void sweepEachStore(java.util.function.Consumer<TaskRecord> consumer) {
        if (taskStore instanceof io.jaiclaw.tasks.persistence.TenantRoutingTaskStore router) {
            // Iterate explicit tenant backends first, then the default.
            for (var entry : router.tenantStores().entrySet()) {
                String tenantId = entry.getKey();
                io.jaiclaw.tasks.persistence.TenantRoutingTaskStore.withTenant(tenantId,
                        () -> entry.getValue().findAll().forEach(consumer));
            }
            // Default store under no tenant context — equivalent to SINGLE-mode.
            router.defaultStore().findAll().forEach(consumer);
            return;
        }
        // Plain (non-routed) store: single pass under whatever context the
        // caller already set up.
        taskStore.findAll().forEach(consumer);
    }

    private void applyPolicy(TaskRecord card, ColumnPolicy policy, String reason) {
        RestartPolicy effective = policy.restartPolicy();
        int attempts = readAttempts(card);
        if (effective == RestartPolicy.REQUEUE
                && policy.maxAttempts() > 0
                && attempts >= policy.maxAttempts()) {
            log.info("Card {} on {}/{} hit maxAttempts={} — escalating to FAIL",
                    card.id(), card.boardId(), card.state(), policy.maxAttempts());
            effective = RestartPolicy.FAIL;
        }
        switch (effective) {
            case FAIL -> {
                if (policy.onFailureEvent() == null || policy.onFailureEvent().isBlank()) {
                    log.warn("FAIL policy on {}/{} but no onFailure event — marking interrupted",
                            card.boardId(), card.state());
                    markInterrupted(card, reason);
                    return;
                }
                transitionService.transition(card.id(), policy.onFailureEvent(), "system/recovery");
            }
            case REQUEUE -> {
                TaskRecord bumped = bumpAttempts(card);
                taskStore.save(bumped);
                // Re-publish a TaskStateChanged with the SAME from/to states
                // (a "RECOVERY" event) so ColumnProcessorManager re-submits
                // the work on the next event-loop turn. No state engine call
                // — the card never leaves its current column, we're just
                // asking the processor to run again under the same
                // idempotency key.
                TransitionRecord rec = new TransitionRecord(
                        card.id(), card.boardId(),
                        card.state(), card.state(),
                        "RECOVERY", "system/recovery",
                        card.tenantId(), Instant.now());
                eventPublisher.publishEvent(new TaskStateChanged(rec, bumped));
                log.info("Card {} on {}/{} REQUEUE-ed (attempt {})",
                        card.id(), card.boardId(), card.state(), attempts + 1);
            }
            case MANUAL -> markInterrupted(card, reason);
        }
    }

    private void markInterrupted(TaskRecord card, String reason) {
        Map<String, String> next = new HashMap<>(card.metadata());
        next.put(INTERRUPTED_META_KEY, "true");
        next.put("kanban.interrupted.reason", reason);
        taskStore.save(rebuildWithMetadata(card, next));
    }

    private TaskRecord bumpAttempts(TaskRecord card) {
        Map<String, String> next = new HashMap<>(card.metadata());
        next.put(ATTEMPT_META_KEY, Integer.toString(readAttempts(card) + 1));
        return rebuildWithMetadata(card, next);
    }

    private static int readAttempts(TaskRecord card) {
        String raw = card.metadata().get(ATTEMPT_META_KEY);
        if (raw == null) return 0;
        try { return Math.max(0, Integer.parseInt(raw)); }
        catch (NumberFormatException e) { return 0; }
    }

    private static TaskRecord rebuildWithMetadata(TaskRecord card, Map<String, String> metadata) {
        return new TaskRecord(card.id(), card.name(), card.description(),
                card.status(), card.deliveryState(), card.result(), card.error(),
                card.flowId(), Map.copyOf(metadata),
                card.createdAt(), card.startedAt(), card.completedAt(), card.tenantId(),
                card.boardId(), card.state(), card.assignee(),
                card.version(), card.orderIndex(), card.idempotencyKey());
    }

    private Map<String, ColumnPolicy> collectProcessorColumns() {
        Map<String, ColumnPolicy> out = new HashMap<>();
        for (BoardDefinition board : boardService.listAllUnscoped()) {
            for (ColumnDefinition column : board.columns()) {
                if (column.processor() != null) {
                    out.put(board.id() + ":" + column.state(), ColumnPolicy.of(column));
                }
            }
        }
        return out;
    }
}
