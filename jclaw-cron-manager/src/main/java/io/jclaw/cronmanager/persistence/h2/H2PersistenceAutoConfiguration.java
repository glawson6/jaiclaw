package io.jclaw.cronmanager.persistence.h2;

import io.jclaw.cron.CronJobStore;
import io.jclaw.cronmanager.persistence.CronExecutionStore;
import io.jclaw.cronmanager.persistence.CronJobDefinitionStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * H2-specific persistence beans. Gated on property so alternative implementations
 * (MySQL, Redis, etc.) can replace this by declaring their own beans with a
 * different {@code havingValue}.
 */
@Configuration
@ConditionalOnProperty(name = "jclaw.cron.persistence", havingValue = "h2", matchIfMissing = true)
class H2PersistenceAutoConfiguration {

    @Bean
    CronJobDefinitionStore cronJobDefinitionStore(JdbcTemplate jdbc) {
        return new H2CronJobDefinitionStore(jdbc);
    }

    @Bean
    CronExecutionStore cronExecutionStore(JdbcTemplate jdbc) {
        return new H2CronExecutionStore(jdbc);
    }

    @Bean
    CronJobStore h2CronJobStore(CronJobDefinitionStore definitionStore) {
        return new H2CronJobStore(definitionStore);
    }
}
