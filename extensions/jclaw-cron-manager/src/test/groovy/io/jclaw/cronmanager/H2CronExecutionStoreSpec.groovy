package io.jclaw.cronmanager

import io.jclaw.cronmanager.model.CronExecutionRecord
import io.jclaw.cronmanager.persistence.h2.H2CronExecutionStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import spock.lang.Specification

import javax.sql.DataSource
import java.time.Instant

class H2CronExecutionStoreSpec extends Specification {

    JdbcTemplate jdbc
    H2CronExecutionStore store

    def setup() {
        DataSource ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:schema.sql")
                .build()
        jdbc = new JdbcTemplate(ds)
        store = new H2CronExecutionStore(jdbc)
    }

    def "insert and findByRunId round-trips a record"() {
        given:
        def record = CronExecutionRecord.started("run1", "job1", "Test Job")

        when:
        store.insert(record)
        def found = store.findByRunId("run1")

        then:
        found.isPresent()
        found.get().runId() == "run1"
        found.get().jobId() == "job1"
        found.get().jobName() == "Test Job"
        found.get().status() == "STARTED"
        found.get().result() == null
        found.get().completedAt() == null
    }

    def "updateStatus transitions from STARTED to COMPLETED"() {
        given:
        store.insert(CronExecutionRecord.started("run1", "job1", "Test"))
        def completedAt = Instant.now()

        when:
        store.updateStatus("run1", "COMPLETED", "All good", completedAt)
        def found = store.findByRunId("run1")

        then:
        found.isPresent()
        found.get().status() == "COMPLETED"
        found.get().result() == "All good"
        found.get().completedAt().epochSecond == completedAt.epochSecond
    }

    def "updateStatus transitions from STARTED to FAILED"() {
        given:
        store.insert(CronExecutionRecord.started("run1", "job1", "Test"))

        when:
        store.updateStatus("run1", "FAILED", "Connection refused", Instant.now())
        def found = store.findByRunId("run1")

        then:
        found.isPresent()
        found.get().status() == "FAILED"
        found.get().result() == "Connection refused"
    }

    def "findByJobId returns records ordered by started_at desc"() {
        given:
        def base = Instant.now()
        store.insert(new CronExecutionRecord("r1", "job1", "Test", "COMPLETED", "ok",
                base.minusSeconds(120), base.minusSeconds(60)))
        store.insert(new CronExecutionRecord("r2", "job1", "Test", "COMPLETED", "ok2",
                base.minusSeconds(60), base))
        store.insert(new CronExecutionRecord("r3", "job1", "Test", "FAILED", "err",
                base, base.plusSeconds(1)))
        // Different job
        store.insert(new CronExecutionRecord("r4", "job2", "Other", "COMPLETED", "ok",
                base, base.plusSeconds(1)))

        when:
        def history = store.findByJobId("job1", 10)

        then:
        history.size() == 3
        history[0].runId() == "r3"  // most recent first
        history[1].runId() == "r2"
        history[2].runId() == "r1"
    }

    def "findByJobId respects limit"() {
        given:
        def base = Instant.now()
        (1..5).each { i ->
            store.insert(new CronExecutionRecord("r${i}", "job1", "Test", "COMPLETED", "ok",
                    base.plusSeconds(i), base.plusSeconds(i + 1)))
        }

        when:
        def history = store.findByJobId("job1", 3)

        then:
        history.size() == 3
    }

    def "findStartedButNotCompleted returns orphaned records"() {
        given:
        store.insert(CronExecutionRecord.started("r1", "job1", "Orphan"))
        store.insert(new CronExecutionRecord("r2", "job1", "Done", "COMPLETED", "ok",
                Instant.now(), Instant.now()))
        store.insert(CronExecutionRecord.started("r3", "job2", "Another Orphan"))

        when:
        def orphaned = store.findStartedButNotCompleted()

        then:
        orphaned.size() == 2
        orphaned.collect { it.runId() }.containsAll(["r1", "r3"])
    }

    def "findByRunId returns empty for nonexistent run"() {
        expect:
        store.findByRunId("nonexistent").isEmpty()
    }
}
