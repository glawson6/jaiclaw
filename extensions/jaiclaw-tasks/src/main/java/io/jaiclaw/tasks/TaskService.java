package io.jaiclaw.tasks;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade for task operations — CRUD, status transitions, and querying.
 */
public class TaskService {

    private final TaskStore taskStore;
    private final FlowStore flowStore;

    public TaskService(TaskStore taskStore, FlowStore flowStore) {
        this.taskStore = taskStore;
        this.flowStore = flowStore;
    }

    public TaskRecord createTask(String name, String description, String tenantId) {
        var task = new TaskRecord(
                UUID.randomUUID().toString(),
                name,
                description,
                TaskStatus.QUEUED,
                TaskDeliveryState.PENDING,
                null, null, null,
                Map.of(),
                Instant.now(),
                null, null,
                tenantId
        );
        taskStore.save(task);
        return task;
    }

    public Optional<TaskRecord> getTask(String id) {
        return taskStore.findById(id);
    }

    public List<TaskRecord> listTasks() {
        return taskStore.findAll();
    }

    public List<TaskRecord> listByStatus(TaskStatus status) {
        return taskStore.findByStatus(status);
    }

    public Optional<TaskRecord> updateStatus(String id, TaskStatus status) {
        return taskStore.findById(id).map(task -> {
            TaskRecord updated = task.withStatus(status);
            taskStore.save(updated);
            return updated;
        });
    }

    public Optional<TaskRecord> completeTask(String id, String result) {
        return taskStore.findById(id).map(task -> {
            TaskRecord updated = task.withResult(result);
            taskStore.save(updated);
            return updated;
        });
    }

    public Optional<TaskRecord> failTask(String id, String error) {
        return taskStore.findById(id).map(task -> {
            TaskRecord updated = task.withError(error);
            taskStore.save(updated);
            return updated;
        });
    }

    public boolean deleteTask(String id) {
        if (taskStore.findById(id).isPresent()) {
            taskStore.deleteById(id);
            return true;
        }
        return false;
    }

    public long count() {
        return taskStore.count();
    }

    // --- Flow operations ---

    public TaskFlow createFlow(String name, List<String> taskIds, String tenantId) {
        var flow = new TaskFlow(
                UUID.randomUUID().toString(),
                name,
                taskIds,
                TaskFlowStatus.PENDING,
                Instant.now(),
                null,
                tenantId
        );
        flowStore.save(flow);
        return flow;
    }

    public Optional<TaskFlow> getFlow(String id) {
        return flowStore.findById(id);
    }

    public List<TaskFlow> listFlows() {
        return flowStore.findAll();
    }
}
