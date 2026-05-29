package io.jaiclaw.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Executes tasks asynchronously. Supports two execution modes:
 * <ul>
 *   <li><b>Camel SEDA</b> (preferred) — uses {@link CamelTaskRoute} when Apache Camel is on the classpath</li>
 *   <li><b>Virtual threads</b> (fallback) — lightweight Java 21 virtual threads when Camel is absent</li>
 * </ul>
 *
 * The executor updates task status in the store as execution progresses:
 * QUEUED → RUNNING → SUCCEEDED/FAILED.
 */
public class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final TaskStore taskStore;

    public TaskExecutor(TaskStore taskStore) {
        this.taskStore = taskStore;
    }

    /**
     * Submit a task for async execution using virtual threads (fallback mode).
     * The handler receives the task and returns a result string.
     */
    public void submit(TaskRecord task, Function<TaskRecord, String> handler) {
        Thread.ofVirtual()
                .name("task-" + task.id())
                .start(() -> executeTask(task, handler));
    }

    /**
     * Execute a task synchronously. Called by both the virtual thread fallback
     * and the Camel SEDA route processor.
     */
    public void executeTask(TaskRecord task, Function<TaskRecord, String> handler) {
        log.info("Starting task: {} ({})", task.name(), task.id());
        taskStore.save(task.withStarted());

        try {
            String result = handler.apply(task);
            taskStore.save(task.withResult(result));
            log.info("Task succeeded: {} ({})", task.name(), task.id());
        } catch (Exception e) {
            log.error("Task failed: {} ({}) - {}", task.name(), task.id(), e.getMessage(), e);
            taskStore.save(task.withError(e.getMessage()));
        }
    }
}
