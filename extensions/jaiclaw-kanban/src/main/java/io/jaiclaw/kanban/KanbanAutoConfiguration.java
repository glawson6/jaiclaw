package io.jaiclaw.kanban;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import io.jaiclaw.kanban.events.KanbanHookFirer;
import io.jaiclaw.kanban.loader.BoardFileLoader;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.persistence.BoardStore;
import io.jaiclaw.kanban.persistence.YamlFileBoardStore;
import io.jaiclaw.kanban.engine.AgentColumnProcessor;
import io.jaiclaw.kanban.engine.ColumnProcessorManager;
import io.jaiclaw.kanban.idempotency.EffectLedger;
import io.jaiclaw.kanban.idempotency.IdempotencyKeyBuilder;
import io.jaiclaw.kanban.mcp.KanbanMcpToolProvider;
import io.jaiclaw.kanban.recovery.KanbanRecoveryManager;
import io.jaiclaw.kanban.recovery.StaleRunningDetector;
import io.jaiclaw.kanban.render.BoardAsciiRenderer;
import io.jaiclaw.kanban.service.BoardSnapshotService;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.kanban.service.TransitionHistory;
import io.jaiclaw.kanban.state.TaskStateEngine;
import io.jaiclaw.kanban.state.TransitionGraphStateEngine;
import io.jaiclaw.kanban.tool.KanbanTools;
import io.jaiclaw.kanban.validation.BoardValidator;
import io.jaiclaw.plugin.HookRunner;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStore;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
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
import java.util.function.Function;

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

    /**
     * Default {@link TaskStateEngine}. The Spring State Machine engine
     * registers via {@link io.jaiclaw.kanban.statemachine.SpringStateMachineConfiguration}
     * and takes precedence over this bean through
     * {@code @ConditionalOnMissingBean}.
     */
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
    public KanbanHookFirer kanbanHookFirer(ObjectProvider<HookRunner> hookRunner,
                                            KanbanProperties properties) {
        return new KanbanHookFirer(hookRunner.getIfAvailable(),
                properties.hooks().legacyMapped());
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

    @Bean
    public BoardSnapshotService boardSnapshotService(
            KanbanBoardService boardService,
            TaskStore taskStore,
            TaskStateEngine engine) {
        return new BoardSnapshotService(boardService, taskStore, engine);
    }

    @Bean
    public BoardAsciiRenderer boardAsciiRenderer() {
        return new BoardAsciiRenderer();
    }

    /**
     * Registers the five kanban agent tools through the shared
     * {@link ToolRegistry}. Only fires when a registry is in the context —
     * embedded usage without an agent stack omits this bean.
     */
    @Bean
    @ConditionalOnBean(ToolRegistry.class)
    public KanbanToolsRegistrar kanbanToolsRegistrar(
            ToolRegistry toolRegistry,
            KanbanBoardService boardService,
            BoardSnapshotService snapshotService,
            TaskTransitionService transitionService,
            TaskStore taskStore,
            BoardAsciiRenderer renderer) {
        KanbanTools.registerAll(toolRegistry, boardService, snapshotService,
                transitionService, taskStore, renderer);
        log.info("Kanban tools registered: board_list, board_show, board_ascii, task_move, task_claim");
        return new KanbanToolsRegistrar();
    }

    /**
     * MCP tool provider — exposes the same five operations to external MCP
     * clients. Gated on {@code McpToolProvider} being on the classpath
     * (lives in {@code jaiclaw-core}, so always on for the embedded path).
     */
    @Bean
    @ConditionalOnClass(name = "io.jaiclaw.core.mcp.McpToolProvider")
    public KanbanMcpToolProvider kanbanMcpToolProvider(
            KanbanBoardService boardService,
            BoardSnapshotService snapshotService,
            TaskTransitionService transitionService,
            TaskStore taskStore,
            BoardAsciiRenderer renderer) {
        return new KanbanMcpToolProvider(boardService, snapshotService,
                transitionService, taskStore, renderer);
    }

    public static class KanbanToolsRegistrar {}

    // ── Phase 3: column processors + recovery + idempotency ────────

    /**
     * The effect ledger keeps recorded processor results so a crash-replay
     * doesn't re-invoke the agent for work that already completed. Lives
     * under {@code jaiclaw.kanban.boards-dir}/.. (one directory level up)
     * so it sits alongside the boards directory without polluting it.
     */
    @Bean
    @ConditionalOnMissingBean
    public EffectLedger effectLedger(KanbanProperties properties) {
        Path boardsDir = Path.of(expandHome(properties.boardsDir()));
        Path ledgerDir = boardsDir.getParent() != null
                ? boardsDir.getParent().resolve("effects")
                : boardsDir.resolveSibling("effects");
        return new EffectLedger(ledgerDir);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotencyKeyBuilder idempotencyKeyBuilder(TransitionHistory history) {
        return new IdempotencyKeyBuilder(history);
    }

    /**
     * Wraps the app-provided agent-runner function (named bean
     * {@code kanbanAgentRunner}) in {@link AgentColumnProcessor}. When no
     * runner bean is present, the processor manager and recovery beans
     * are not created — kanban remains a pure card/board library.
     */
    @Bean
    @ConditionalOnBean(name = "kanbanAgentRunner")
    @ConditionalOnProperty(prefix = "jaiclaw.kanban.processors",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    public AgentColumnProcessor agentColumnProcessor(
            @org.springframework.beans.factory.annotation.Qualifier("kanbanAgentRunner")
            Function<TaskRecord, String> kanbanAgentRunner,
            IdempotencyKeyBuilder keyBuilder,
            EffectLedger ledger) {
        return new AgentColumnProcessor(kanbanAgentRunner, keyBuilder, ledger);
    }

    @Bean
    @ConditionalOnBean(AgentColumnProcessor.class)
    public ColumnProcessorManager columnProcessorManager(
            KanbanBoardService boardService,
            TaskStore taskStore,
            TaskTransitionService transitionService,
            AgentColumnProcessor processor) {
        return new ColumnProcessorManager(boardService, taskStore,
                transitionService, processor);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jaiclaw.kanban.recovery",
            name = "enabled", havingValue = "true", matchIfMissing = true)
    public KanbanRecoveryManager kanbanRecoveryManager(
            KanbanBoardService boardService,
            TaskStore taskStore,
            TaskTransitionService transitionService,
            KanbanProperties properties,
            ApplicationEventPublisher publisher) {
        return new KanbanRecoveryManager(boardService, taskStore,
                transitionService, properties, publisher);
    }

    @Bean
    @ConditionalOnBean(KanbanRecoveryManager.class)
    public StaleRunningDetector staleRunningDetector(KanbanRecoveryManager manager,
                                                     KanbanProperties properties) {
        return new StaleRunningDetector(manager, properties);
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
