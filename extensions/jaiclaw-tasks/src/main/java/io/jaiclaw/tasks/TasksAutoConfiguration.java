package io.jaiclaw.tasks;

import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;

/**
 * Auto-configuration for task management. Disabled by default.
 *
 * <p>Supports two execution modes:
 * <ul>
 *   <li><b>Camel SEDA</b> — when {@code camel-spring-boot-starter} is on the classpath,
 *       tasks are submitted to a SEDA queue for async multi-consumer execution</li>
 *   <li><b>Virtual threads</b> — fallback when Camel is absent, using Java 21 virtual threads</li>
 * </ul>
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
@ConditionalOnProperty(name = "jaiclaw.tasks.enabled", havingValue = "true", matchIfMissing = false)
public class TasksAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TasksAutoConfiguration.class);

    @Bean
    public JsonFileTaskStore taskStore(Environment env) {
        String storageDir = env.getProperty("jaiclaw.tasks.storage-dir",
                System.getProperty("user.home") + "/.jaiclaw/tasks");
        return new JsonFileTaskStore(Path.of(storageDir));
    }

    @Bean
    public JsonFileFlowStore flowStore(Environment env) {
        String storageDir = env.getProperty("jaiclaw.tasks.storage-dir",
                System.getProperty("user.home") + "/.jaiclaw/tasks");
        return new JsonFileFlowStore(Path.of(storageDir));
    }

    @Bean
    public TaskService taskService(TaskStore taskStore, FlowStore flowStore) {
        return new TaskService(taskStore, flowStore);
    }

    @Bean
    public TaskExecutor taskExecutor(TaskStore taskStore) {
        return new TaskExecutor(taskStore);
    }

    @Bean
    public TaskToolsRegistrar taskToolsRegistrar(TaskService service, ToolRegistry toolRegistry) {
        TaskTools.registerAll(toolRegistry, service);
        log.info("Task tools registered: task_create, task_list, task_get, task_update, task_delete");
        return new TaskToolsRegistrar();
    }

    /**
     * Camel SEDA execution mode — activated when Camel is on the classpath.
     */
    @Configuration
    @ConditionalOnClass(name = "org.apache.camel.CamelContext")
    static class CamelTaskConfiguration {

        @Bean
        public CamelTaskRoute camelTaskRoute(
                org.apache.camel.CamelContext camelContext,
                org.apache.camel.ProducerTemplate producerTemplate,
                TaskExecutor taskExecutor) throws Exception {
            var route = new CamelTaskRoute(camelContext, producerTemplate, taskExecutor);
            route.start();
            log.info("Task executor using Camel SEDA mode");
            return route;
        }
    }

    public static class TaskToolsRegistrar {}
}
