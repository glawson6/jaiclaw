package io.jaiclaw.examples.kanbandemo;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.tasks.JsonFileTaskStore;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Wires the kanban demo's two app-provided beans: a stub agent runner
 * and a {@link TaskStore} so the kanban autoconfig fires without
 * pulling in the full tasks autoconfig stack.
 *
 * <p>The kanban {@code ColumnProcessorManager} expects a
 * {@code Function<TaskRecord, String>} bean named {@code kanbanAgentRunner}
 * (see {@link io.jaiclaw.kanban.KanbanAutoConfiguration#agentColumnProcessor}).
 * This demo provides a deterministic stub — "DRAFT: {original name}" — so
 * the demo runs without an LLM key and the e2e skill can byte-match its
 * output.
 */
@Configuration
public class KanbanDemoBeans {

    /** Stub agent runner — deterministic, no LLM key needed. */
    @Bean(name = "kanbanAgentRunner")
    public Function<TaskRecord, String> kanbanAgentRunner() {
        return card -> "DRAFT: " + (card.name() == null ? "" : card.name());
    }

    /**
     * Provide a {@link TaskStore} directly so
     * {@code KanbanAutoConfiguration}'s {@code @ConditionalOnBean(TaskStore.class)}
     * gate fires without pulling in the full {@code TasksAutoConfiguration}
     * stack (which requires a {@code ToolRegistry}). Same pattern used by
     * the kanban module's integration specs.
     */
    @Bean
    public TaskStore demoTaskStore(@Value("${jaiclaw.tasks.demo.dir:${java.io.tmpdir}/kanban-demo/tasks}")
                                   String dir) {
        return new JsonFileTaskStore(Path.of(dir),
                new TenantGuard(TenantProperties.DEFAULT));
    }
}
