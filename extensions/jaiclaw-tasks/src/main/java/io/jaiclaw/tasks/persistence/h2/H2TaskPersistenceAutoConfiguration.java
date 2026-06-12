package io.jaiclaw.tasks.persistence.h2;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.tasks.TaskStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 4 H2 persistence wiring for {@link TaskStore}.
 *
 * <p>Activated by:
 * <ul>
 *   <li>{@code jaiclaw.tasks.storage.type=h2} on the environment</li>
 *   <li>{@code spring-jdbc} + a {@code DataSource} bean on the classpath</li>
 *   <li>{@code com.h2database.h2} on the classpath</li>
 * </ul>
 *
 * <p>{@code @AutoConfigureBefore(TasksAutoConfiguration)} so the H2 bean
 * registers before the default JSON store's {@code @ConditionalOnMissingBean}
 * fires (cron-manager precedent).
 *
 * <p>The schema lives at {@code src/main/resources/schema.sql} and is
 * applied by Spring Boot's standard SQL init when
 * {@code spring.sql.init.mode=always} (or {@code embedded} with H2).
 */
@AutoConfiguration
@AutoConfigureBefore(name = "io.jaiclaw.tasks.TasksAutoConfiguration")
@ConditionalOnClass(name = {
        "org.springframework.jdbc.core.JdbcTemplate",
        "org.h2.Driver"
})
@ConditionalOnProperty(prefix = "jaiclaw.tasks.storage",
        name = "type", havingValue = "h2")
public class H2TaskPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnMissingBean(TaskStore.class)
    public TaskStore h2TaskStore(JdbcTemplate jdbc,
                                  ObjectProvider<TenantGuard> tenantGuard) {
        return new H2TaskStore(jdbc, tenantGuard.getIfAvailable());
    }
}
