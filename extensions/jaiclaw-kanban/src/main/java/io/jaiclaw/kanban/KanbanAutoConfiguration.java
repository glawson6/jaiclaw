package io.jaiclaw.kanban;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.kanban.events.KanbanHookFirer;
import io.jaiclaw.kanban.loader.BoardFileLoader;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.persistence.BoardStore;
import io.jaiclaw.kanban.persistence.YamlFileBoardStore;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-configuration for the kanban extension. Disabled by default — the
 * whole module is opt-in via {@code jaiclaw.kanban.enabled=true} per the
 * implementation plan's Definition-of-Done.
 *
 * <p>Wires up the graph state engine, board service, transition service,
 * history, mapped hook firer, the YAML-backed {@link BoardStore}
 * (analysis §9 Q1 resolution), and the legacy
 * {@link BoardFileLoader} for {@code locations.patterns}-driven loads
 * (classpath, additional file globs). Phase 2 layers REST/SSE/MCP/Actuator
 * on top in separate {@code @Configuration} classes; Phase 3 adds the
 * column processor manager + recovery; Phase 4 promotes the kanban event
 * to a first-class {@code HookEvent} subtype.
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
    @ConditionalOnMissingBean
    public BoardStore boardStore(KanbanProperties properties) {
        return new YamlFileBoardStore(Path.of(expandHome(properties.boardsDir())));
    }

    @Bean
    public KanbanBoardService kanbanBoardService(
            TenantGuard tenantGuard,
            BoardStore boardStore,
            KanbanProperties properties) {
        return new KanbanBoardService(tenantGuard, boardStore, properties.boards().writable());
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
     * Loads boards on startup from two sources, validates the merged list,
     * and caches everything in {@link KanbanBoardService} without echoing
     * disk-loaded boards back to disk.
     *
     * <ol>
     *   <li>Boards persisted in the {@link BoardStore} (the YAML files in
     *       {@code jaiclaw.kanban.boards-dir} — these are the durable
     *       record, written by REST when {@code boards.writable=true}).</li>
     *   <li>Boards loaded through the legacy
     *       {@code jaiclaw.kanban.locations.patterns} (classpath:* / file:
     *       globs — useful for shipping boards inside JARs or pointing at
     *       an alternate directory).</li>
     * </ol>
     *
     * <p>On id conflict, the store wins (a YAML in {@code boards-dir} is
     * the authoritative copy that REST can mutate; classpath boards are
     * fallback defaults).
     */
    @Bean
    public KanbanBootstrapRunner kanbanBootstrapRunner(
            KanbanProperties properties,
            BoardStore boardStore,
            BoardFileLoader loader,
            BoardValidator validator,
            KanbanBoardService boardService) {
        return new KanbanBootstrapRunner(properties, boardStore, loader, validator, boardService);
    }

    public static class KanbanBootstrapRunner {

        public KanbanBootstrapRunner(
                KanbanProperties properties,
                BoardStore boardStore,
                BoardFileLoader loader,
                BoardValidator validator,
                KanbanBoardService boardService) {
            // Order matters for conflict resolution: locations first (overridable),
            // then boardStore (authoritative).
            Map<String, BoardDefinition> merged = new LinkedHashMap<>();
            for (BoardDefinition d : loader.loadAll(properties.locations().patterns())) {
                merged.put(d.id(), d);
            }
            for (BoardDefinition d : boardStore.findAll()) {
                merged.put(d.id(), d);
            }
            List<BoardDefinition> all = new ArrayList<>(merged.values());
            if (all.isEmpty()) {
                log.info("Kanban: no boards loaded (store dir={}, patterns={})",
                        properties.boardsDir(), properties.locations().patterns());
                return;
            }
            validator.validateOrThrow(all);
            boardService.cacheAll(all);
            log.info("Kanban: cached {} boards (store dir={}, patterns={})",
                    all.size(), properties.boardsDir(), properties.locations().patterns());
        }
    }

    private static String expandHome(String path) {
        if (path == null) return null;
        if (path.startsWith("~/")) return System.getProperty("user.home") + path.substring(1);
        return path;
    }
}
