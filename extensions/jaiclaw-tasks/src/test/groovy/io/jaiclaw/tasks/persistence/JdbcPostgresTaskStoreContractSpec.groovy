package io.jaiclaw.tasks.persistence

import io.jaiclaw.tasks.TaskStore
import io.jaiclaw.tasks.persistence.h2.H2TaskStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

import java.nio.charset.StandardCharsets

/**
 * Plan §9 group 4b — pins the H2TaskStore class (used both for H2 and
 * Postgres because the SQL is portable) against the shared
 * {@link TaskStoreContractSpec} on a real Postgres backend. Uses
 * Testcontainers — first Testcontainers integration in the jaiclaw repo.
 *
 * <p>Requires a running Docker daemon. The Postgres container starts
 * once per spec class (Shared) so the 10 contract tests don't pay the
 * per-method startup cost. Each test truncates the table to start
 * fresh.
 */
class JdbcPostgresTaskStoreContractSpec extends TaskStoreContractSpec {

    @Shared
    PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("jaiclaw_test")
            .withUsername("jaiclaw")
            .withPassword("jaiclaw")

    @Shared
    JdbcTemplate jdbc

    def setupSpec() {
        postgres.start()
        def ds = new SimpleDriverDataSource(
                new org.postgresql.Driver(),
                postgres.jdbcUrl,
                postgres.username,
                postgres.password)
        jdbc = new JdbcTemplate(ds)
        // Apply the Postgres-flavoured schema (CLOB → TEXT).
        def sql = getClass().getResourceAsStream("/schema-postgres.sql")
                .getText(StandardCharsets.UTF_8.name())
        jdbc.execute(sql)
    }

    def cleanupSpec() {
        postgres.stop()
    }

    @Override
    TaskStore createStore() {
        // Each test method gets a clean table — no method-level container
        // restarts, just a truncate.
        jdbc.execute("TRUNCATE TABLE jaiclaw_tasks")
        return new H2TaskStore(jdbc)
    }
}
