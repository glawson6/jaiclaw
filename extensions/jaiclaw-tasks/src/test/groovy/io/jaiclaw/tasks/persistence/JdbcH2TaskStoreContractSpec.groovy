package io.jaiclaw.tasks.persistence

import io.jaiclaw.tasks.TaskStore
import org.h2.Driver
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the H2-backed JDBC store (built through
 * {@link JdbcTaskStoreProvider} → {@link io.jaiclaw.tasks.persistence.h2.H2TaskStore})
 * against the shared {@link TaskStoreContractSpec}. Two identical
 * provider subclasses must produce stores that pass the same suite —
 * that's what stops semantic drift across backends.
 */
class JdbcH2TaskStoreContractSpec extends TaskStoreContractSpec {

    static final AtomicInteger DB_SEQ = new AtomicInteger(0)

    @Override
    TaskStore createStore() {
        String url = "jdbc:h2:mem:contract-${DB_SEQ.incrementAndGet()};DB_CLOSE_DELAY=-1"
        def ds = new SimpleDriverDataSource(new Driver(), url, "sa", "")
        def jdbc = new JdbcTemplate(ds)
        // Apply the same schema the production autoconfig loads.
        def sql = getClass().getResourceAsStream("/schema.sql")
                .getText(StandardCharsets.UTF_8.name())
        jdbc.execute(sql)
        // Hand the JDBC datasource off to JdbcTaskStoreProvider via the SPI
        // — that's the actual code path apps take. We bypass the URL/user
        // config because we already have a configured datasource; build
        // the H2TaskStore directly with the same wiring the provider does.
        return new io.jaiclaw.tasks.persistence.h2.H2TaskStore(jdbc)
    }
}
