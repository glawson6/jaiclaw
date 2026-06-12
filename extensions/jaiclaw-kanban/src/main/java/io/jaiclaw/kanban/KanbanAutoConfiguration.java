package io.jaiclaw.kanban;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.kanban.events.KanbanHookFirer;
import io.jaiclaw.kanban.loader.BoardFileLoader;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.kanban.service.TransitionHistory;
import io.jaiclaw.kanban.state.TaskStateEngine;
import io.jaiclaw.kanban.state.TransitionGraphStateEngine;
import io.jaiclaw.kanban.validation.BoardValidator;
import io.jaiclaw.plugin.HookRunner;
import io.jaiclaw.tasks.TaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import java.util.List;

/**
 * Auto-configuration for the kanban extension. Disabled by default — the
 * whole module is opt-in via {@code jaiclaw.kanban.enabled=true} per the
 * implementation plan's Definition-of-Done.
 *
 * <p>Wires up the graph state engine, board service, transition service,
 * history, mapped hook firer, and the board file loader. Phase 2 layers
 * REST/SSE/MCP/Actuator on top in separate {@code @Configuration} classes;
 * Phase 3 adds the column processor manager + recovery; Phase 4 promotes
 * the kanban event to a first-class {@code HookEvent} subtype.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.tasks.TasksAutoConfiguration")
@ConditionalOnBean(TaskStore.class)
@ConditionalOnProperty(name = "jaiclaw.kanban.enabled", havingValue = "true")
@EnableConfigurationProperties(KanbanProperties.class)
public class KanbanAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KanbanAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TenantGuard kanbanFallbackTenantGuard() {
        // Only used when no other module has registered a TenantGuard.
        return new TenantGuard(TenantProperties.DEFAULT);
    }

    @Bean
    @ConditionalOnMissingBean
    public TaskStateEngine taskStateEngine() {
        return new TransitionGraphStateEngine();
    }

    @Bean
    public BoardValidator boardValidator() {
        return new BoardValidator();
    }

    @Bean
    public BoardFileLoader boardFileLoader(ResourceLoader resourceLoader) {
        return new BoardFileLoader(resourceLoader);
    }

    @Bean
    public KanbanBoardService kanbanBoardService(TenantGuard tenantGuard) {
        return new KanbanBoardService(tenantGuard);
    }

    @Bean
    public TransitionHistory transitionHistory(KanbanProperties properties) {
        return new TransitionHistory(properties.history().maxPerBoard());
    }

    @Bean
    public KanbanHookFirer kanbanHookFirer(ObjectProvider<HookRunner> hookRunner) {
        return new KanbanHookFirer(hookRunner.getIfAvailable());
    }

    @Bean
    public TaskTransitionService taskTransitionService(
            TaskStore taskStore,
            KanbanBoardService boardService,
            TaskStateEngine engine,
            TransitionHistory history,
            ApplicationEventPublisher publisher,
            KanbanHookFirer hookFirer,
            TenantGuard tenantGuard) {
        return new TaskTransitionService(taskStore, boardService, engine, history,
                publisher, hookFirer, tenantGuard);
    }

    /**
     * Loads board YAML on startup, validates everything, then registers with
     * the board service. Failing fast at boot is much friendlier than
     * letting a misconfigured board explode mid-transition.
     */
    @Bean
    public KanbanBootstrapRunner kanbanBootstrapRunner(
            KanbanProperties properties,
            BoardFileLoader loader,
            BoardValidator validator,
            KanbanBoardService boardService) {
        return new KanbanBootstrapRunner(properties, loader, validator, boardService);
    }

    /**
     * One-shot startup bean. Implemented inline to keep the wiring visible —
     * boards loaded, validated, registered, logged.
     */
    public static class KanbanBootstrapRunner {

        public KanbanBootstrapRunner(
                KanbanProperties properties,
                BoardFileLoader loader,
                BoardValidator validator,
                KanbanBoardService boardService) {
            List<String> patterns = properties.locations().patterns();
            List<BoardDefinition> defs = loader.loadAll(patterns);
            if (defs.isEmpty()) {
                log.info("Kanban: no boards loaded from patterns {}", patterns);
                return;
            }
            validator.validateOrThrow(defs);
            boardService.registerAll(defs);
            log.info("Kanban: registered {} boards", defs.size());
        }
    }
}
