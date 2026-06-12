package io.jaiclaw.kanban.persistence.h2;

import io.jaiclaw.kanban.persistence.BoardStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 4 H2 persistence wiring for {@link BoardStore}.
 *
 * <p>Activated by {@code jaiclaw.kanban.boards.type=h2} +
 * {@code spring-jdbc} + {@code h2} on the classpath.
 *
 * <p>{@code @AutoConfigureBefore(KanbanAutoConfiguration)} so the H2 bean
 * registers before the default YAML store's {@code @ConditionalOnMissingBean}
 * fires.
 *
 * <p>This module ships its kanban-specific schema as a separate file
 * ({@code schema-kanban.sql}) to avoid clashing with the
 * {@code jaiclaw-tasks} schema when both modules are on the same
 * classpath. Apps should set
 * {@code spring.sql.init.schema-locations=classpath:schema.sql,classpath:schema-kanban.sql}
 * when both H2 stores are enabled.
 */
@AutoConfiguration
@AutoConfigureBefore(name = "io.jaiclaw.kanban.KanbanAutoConfiguration")
@ConditionalOnClass(name = {
        "org.springframework.jdbc.core.JdbcTemplate",
        "org.h2.Driver"
})
@ConditionalOnProperty(prefix = "jaiclaw.kanban.boards",
        name = "type", havingValue = "h2")
public class H2BoardPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(BoardStore.class)
    public BoardStore h2BoardStore(JdbcTemplate jdbc) {
        return new H2BoardStore(jdbc);
    }
}
