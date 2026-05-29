package io.jaiclaw.tasks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * A task flow is an ordered sequence of tasks forming a pipeline.
 *
 * @param id          unique flow identifier
 * @param name        human-readable flow name
 * @param taskIds     ordered list of task IDs in this flow
 * @param status      current flow status
 * @param createdAt   when the flow was created
 * @param completedAt when the flow completed (null until done)
 * @param tenantId    tenant ID for multi-tenancy
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskFlow(
        String id,
        String name,
        List<String> taskIds,
        TaskFlowStatus status,
        Instant createdAt,
        Instant completedAt,
        String tenantId
) {
    public TaskFlow {
        if (taskIds == null) taskIds = List.of();
        if (status == null) status = TaskFlowStatus.PENDING;
    }

    public TaskFlow withStatus(TaskFlowStatus newStatus) {
        Instant completed = newStatus == TaskFlowStatus.COMPLETED || newStatus == TaskFlowStatus.FAILED
                ? Instant.now() : completedAt;
        return new TaskFlow(id, name, taskIds, newStatus, createdAt, completed, tenantId);
    }
}
