package io.jaiclaw.tasks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/**
 * A task record representing a unit of async work.
 *
 * @param id            unique task identifier
 * @param name          human-readable task name
 * @param description   task description or instructions
 * @param status        current lifecycle status
 * @param deliveryState delivery state of results
 * @param result        task result/output (null until completed or failed)
 * @param error         error message (null unless failed)
 * @param flowId        parent flow ID (null if standalone)
 * @param metadata      arbitrary key-value metadata
 * @param createdAt     when the task was created
 * @param startedAt     when execution started (null until running)
 * @param completedAt   when execution finished (null until done)
 * @param tenantId      tenant ID for multi-tenancy
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
        String tenantId
) {
    public TaskRecord {
        if (status == null) status = TaskStatus.QUEUED;
        if (deliveryState == null) deliveryState = TaskDeliveryState.PENDING;
        if (metadata == null) metadata = Map.of();
    }

    public TaskRecord withStatus(TaskStatus newStatus) {
        return new TaskRecord(id, name, description, newStatus, deliveryState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId);
    }

    public TaskRecord withResult(String newResult) {
        return new TaskRecord(id, name, description, TaskStatus.SUCCEEDED, deliveryState,
                newResult, null, flowId, metadata, createdAt, startedAt, Instant.now(), tenantId);
    }

    public TaskRecord withError(String newError) {
        return new TaskRecord(id, name, description, TaskStatus.FAILED, deliveryState,
                null, newError, flowId, metadata, createdAt, startedAt, Instant.now(), tenantId);
    }

    public TaskRecord withStarted() {
        return new TaskRecord(id, name, description, TaskStatus.RUNNING, deliveryState,
                result, error, flowId, metadata, createdAt, Instant.now(), completedAt, tenantId);
    }

    public TaskRecord withDeliveryState(TaskDeliveryState newState) {
        return new TaskRecord(id, name, description, status, newState, result, error,
                flowId, metadata, createdAt, startedAt, completedAt, tenantId);
    }
}
