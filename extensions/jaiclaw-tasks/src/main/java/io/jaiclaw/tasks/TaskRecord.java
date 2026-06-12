package io.jaiclaw.tasks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/**
 * A task record representing a unit of async work.
 *
 * @param id              unique task identifier
 * @param name            human-readable task name
 * @param description     task description or instructions
 * @param status          current lifecycle status (coarse phase)
 * @param deliveryState   delivery state of results
 * @param result          task result/output (null until completed or failed)
 * @param error           error message (null unless failed)
 * @param flowId          parent flow ID (null if standalone)
 * @param metadata        arbitrary key-value metadata
 * @param createdAt       when the task was created
 * @param startedAt       when execution started (null until running)
 * @param completedAt     when execution finished (null until done)
 * @param tenantId        tenant ID for multi-tenancy
 * @param boardId         kanban board this card belongs to (null for non-board tasks)
 * @param state           kanban state string within the board (null for non-board tasks)
 * @param assignee        assignee handle (null if unassigned)
 * @param version         optimistic-locking version, incremented on every save
 * @param orderIndex      ordering hint within a column (0 if unset)
 * @param idempotencyKey  stable retry key for processor executions (null if untouched)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskRecord(
        String id,
        String name,
        String description,
        TaskStatus status,
        TaskDeliveryState deliveryState,
        String result,
        String error,
        String flowId,
        Map<String, String> metadata,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String tenantId,
        String boardId,
        String state,
        String assignee,
        long version,
        int orderIndex,
        String idempotencyKey
) {
    public TaskRecord {
        if (status == null) status = TaskStatus.QUEUED;
        if (deliveryState == null) deliveryState = TaskDeliveryState.PENDING;
        if (metadata == null) metadata = Map.of();
    }

    /**
     * Legacy 13-argument constructor for non-board tasks. Existing call sites
     * (cron, calendar, pipeline, and pre-kanban specs) keep compiling unchanged
     * while new board-aware fields default to {@code null}/{@code 0}.
     */
    public TaskRecord(
            String id,
            String name,
            String description,
            TaskStatus status,
            TaskDeliveryState deliveryState,
            String result,
            String error,
            String flowId,
            Map<String, String> metadata,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            String tenantId
    ) {
        this(id, name, description, status, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                null, null, null, 0L, 0, null);
    }

    public TaskRecord withStatus(TaskStatus newStatus) {
        return new TaskRecord(id, name, description, newStatus, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                boardId, state, assignee, version, orderIndex, idempotencyKey);
    }

    public TaskRecord withResult(String newResult) {
        return new TaskRecord(id, name, description, TaskStatus.SUCCEEDED, deliveryState,
                newResult, null, flowId, metadata, createdAt, startedAt, Instant.now(), tenantId,
                boardId, state, assignee, version, orderIndex, idempotencyKey);
    }

    public TaskRecord withError(String newError) {
        return new TaskRecord(id, name, description, TaskStatus.FAILED, deliveryState,
                null, newError, flowId, metadata, createdAt, startedAt, Instant.now(), tenantId,
                boardId, state, assignee, version, orderIndex, idempotencyKey);
    }

    public TaskRecord withStarted() {
        return new TaskRecord(id, name, description, TaskStatus.RUNNING, deliveryState,
                result, error, flowId, metadata, createdAt, Instant.now(), completedAt, tenantId,
                boardId, state, assignee, version, orderIndex, idempotencyKey);
    }

    public TaskRecord withDeliveryState(TaskDeliveryState newState) {
        return new TaskRecord(id, name, description, status, newState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                boardId, state, assignee, version, orderIndex, idempotencyKey);
    }

    public TaskRecord withBoardId(String newBoardId) {
        return new TaskRecord(id, name, description, status, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                newBoardId, state, assignee, version, orderIndex, idempotencyKey);
    }

    public TaskRecord withState(String newState) {
        return new TaskRecord(id, name, description, status, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                boardId, newState, assignee, version, orderIndex, idempotencyKey);
    }

    public TaskRecord withAssignee(String newAssignee) {
        return new TaskRecord(id, name, description, status, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                boardId, state, newAssignee, version, orderIndex, idempotencyKey);
    }

    public TaskRecord withVersion(long newVersion) {
        return new TaskRecord(id, name, description, status, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                boardId, state, assignee, newVersion, orderIndex, idempotencyKey);
    }

    public TaskRecord withOrderIndex(int newOrderIndex) {
        return new TaskRecord(id, name, description, status, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                boardId, state, assignee, version, newOrderIndex, idempotencyKey);
    }

    public TaskRecord withIdempotencyKey(String newKey) {
        return new TaskRecord(id, name, description, status, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId,
                boardId, state, assignee, version, orderIndex, newKey);
    }
}
