package io.jaiclaw.kanban.web;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.kanban.KanbanProperties;
import io.jaiclaw.kanban.render.BoardAsciiRenderer;
import io.jaiclaw.kanban.service.BoardSnapshotService;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.kanban.service.TransitionHistory;
import io.jaiclaw.kanban.validation.BoardValidator;
import io.jaiclaw.tasks.TaskStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers the kanban REST controller when Spring Web is on the classpath
 * and {@code jaiclaw.kanban.http.enabled} is true (default true).
 * Separated from {@code KanbanAutoConfiguration} so the engine module
 * loads cleanly without {@code spring-web}.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.kanban.KanbanAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestController")
@ConditionalOnProperty(
        prefix = "jaiclaw.kanban.http",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class KanbanWebConfiguration {

    @Bean
    @ConditionalOnBean(KanbanBoardService.class)
    public KanbanBoardController kanbanBoardController(
            KanbanBoardService boardService,
            BoardSnapshotService snapshotService,
            TaskTransitionService transitionService,
            TransitionHistory history,
            TaskStore taskStore,
            BoardValidator validator,
            BoardAsciiRenderer renderer) {
        return new KanbanBoardController(
                boardService, snapshotService, transitionService,
                history, taskStore, validator, renderer);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jaiclaw.kanban.sse", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnBean(BoardSnapshotService.class)
    public KanbanEventBroadcaster kanbanEventBroadcaster(
            BoardSnapshotService snapshotService,
            TenantGuard tenantGuard,
            KanbanProperties properties) {
        return new KanbanEventBroadcaster(snapshotService, tenantGuard, properties.sse());
    }

    @Bean
    @ConditionalOnBean(KanbanEventBroadcaster.class)
    public KanbanEventController kanbanEventController(
            KanbanEventBroadcaster broadcaster,
            KanbanBoardService boardService) {
        return new KanbanEventController(broadcaster, boardService);
    }
}
