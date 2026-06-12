package io.jaiclaw.kanban.statemachine;

import io.jaiclaw.kanban.state.TaskStateEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Wires the optional {@link SpringStateMachineEngine} when both the
 * classpath has Spring State Machine and {@code jaiclaw.kanban.engine}
 * is set to {@code spring-statemachine}.
 *
 * <p>Configured to fire <b>before</b> {@code KanbanAutoConfiguration} so
 * its {@code @ConditionalOnMissingBean TaskStateEngine} default is
 * suppressed when the SSM engine is present.
 */
@AutoConfiguration
@AutoConfigureBefore(name = "io.jaiclaw.kanban.KanbanAutoConfiguration")
@ConditionalOnClass(name = "org.springframework.statemachine.StateMachine")
@ConditionalOnProperty(prefix = "jaiclaw.kanban.engine",
        name = "name", havingValue = "spring-statemachine")
public class SpringStateMachineConfiguration {

    @Bean
    public BoardStateMachineFactory boardStateMachineFactory() {
        return new BoardStateMachineFactory();
    }

    @Bean
    public TaskStateEngine springStateMachineEngine(BoardStateMachineFactory factory) {
        return new SpringStateMachineEngine(factory);
    }
}
