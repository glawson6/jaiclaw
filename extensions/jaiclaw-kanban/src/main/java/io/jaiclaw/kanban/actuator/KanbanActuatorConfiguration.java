package io.jaiclaw.kanban.actuator;

import io.jaiclaw.kanban.KanbanProperties;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TransitionHistory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link KanbanActuatorEndpoint} when Spring Boot Actuator is
 * on the classpath. Gated on {@code jaiclaw.kanban.actuator.enabled}
 * (default true), so projects that don't want operator visibility can
 * keep the endpoint off without removing the Actuator dependency.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.kanban.KanbanAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@ConditionalOnProperty(
        prefix = "jaiclaw.kanban.actuator",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class KanbanActuatorConfiguration {

    @Bean
    @ConditionalOnBean(KanbanBoardService.class)
    public KanbanActuatorEndpoint kanbanActuatorEndpoint(
            KanbanBoardService boardService,
            TransitionHistory history,
            KanbanProperties properties) {
        return new KanbanActuatorEndpoint(boardService, history, properties);
    }
}
