package io.jaiclaw.tasks;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Camel SEDA-based task execution route. Tasks are submitted to a SEDA queue
 * and processed asynchronously by concurrent consumers.
 *
 * <p>This leverages Camel's built-in SEDA queue for:
 * <ul>
 *   <li>Async, multi-consumer task execution</li>
 *   <li>Backpressure via bounded queue size</li>
 *   <li>Error handling with dead-letter channel support</li>
 * </ul>
 */
public class CamelTaskRoute {

    private static final Logger log = LoggerFactory.getLogger(CamelTaskRoute.class);

    static final String SEDA_ENDPOINT = "seda:jaiclaw-tasks?size=100&concurrentConsumers=5";
    static final String ROUTE_ID = "jaiclaw-task-executor";
    static final String HEADER_TASK_ID = "JaiClawTaskId";

    private final CamelContext camelContext;
    private final ProducerTemplate producerTemplate;
    private final TaskExecutor taskExecutor;
    private final Map<String, TaskSubmission> pendingHandlers = new ConcurrentHashMap<>();

    public CamelTaskRoute(CamelContext camelContext,
                          ProducerTemplate producerTemplate,
                          TaskExecutor taskExecutor) {
        this.camelContext = camelContext;
        this.producerTemplate = producerTemplate;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Start the SEDA consumer route.
     */
    public void start() throws Exception {
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(SEDA_ENDPOINT)
                        .routeId(ROUTE_ID)
                        .process(CamelTaskRoute.this::processTask);
            }
        });
        log.info("Task executor SEDA route started: {}", SEDA_ENDPOINT);
    }

    /**
     * Submit a task to the SEDA queue for async execution.
     */
    public void submit(TaskRecord task, Function<TaskRecord, String> handler) {
        pendingHandlers.put(task.id(), new TaskSubmission(task, handler));
        producerTemplate.sendBodyAndHeader(SEDA_ENDPOINT, task.id(), HEADER_TASK_ID, task.id());
    }

    private void processTask(Exchange exchange) {
        String taskId = exchange.getIn().getHeader(HEADER_TASK_ID, String.class);
        TaskSubmission submission = pendingHandlers.remove(taskId);
        if (submission == null) {
            log.warn("No handler found for task: {}", taskId);
            return;
        }
        taskExecutor.executeTask(submission.task(), submission.handler());
    }

    record TaskSubmission(TaskRecord task, Function<TaskRecord, String> handler) {}
}
