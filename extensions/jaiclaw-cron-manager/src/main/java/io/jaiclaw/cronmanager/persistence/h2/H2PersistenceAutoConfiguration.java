package io.jaiclaw.cronmanager.persistence.h2;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.cron.CronJobStore;
import io.jaiclaw.cronmanager.persistence.CronExecutionStore;
import io.jaiclaw.cronmanager.persistence.CronJobDefinitionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * H2-specific persistence beans. Gated on property so alternative implementations
 * (MySQL, Redis, etc.) can replace this by declaring their own beans with a
 * different {@code havingValue}.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "jaiclaw.cron.persistence", havingValue = "h2", matchIfMissing = true)
class H2PersistenceAutoConfiguration {

    @Bean
    CronJobDefinitionStore cronJobDefinitionStore(JdbcTemplate jdbc, TenantGuard tenantGuard) {
        return new H2CronJobDefinitionStore(jdbc, tenantGuard);
    }

    @Bean
    CronExecutionStore cronExecutionStore(JdbcTemplate jdbc, TenantGuard tenantGuard) {
        return new H2CronExecutionStore(jdbc, tenantGuard);
    }

    @Bean
    CronJobStore h2CronJobStore(CronJobDefinitionStore definitionStore) {
        return new H2CronJobStore(definitionStore);
    }
}
