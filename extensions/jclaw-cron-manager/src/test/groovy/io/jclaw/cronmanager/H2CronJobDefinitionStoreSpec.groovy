package io.jclaw.cronmanager

import io.jclaw.core.model.CronJob
import io.jclaw.core.tool.ToolProfile
import io.jclaw.cronmanager.model.CronJobDefinition
import io.jclaw.cronmanager.persistence.h2.H2CronJobDefinitionStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import spock.lang.Specification

import javax.sql.DataSource
import java.time.Instant

class H2CronJobDefinitionStoreSpec extends Specification {

    JdbcTemplate jdbc
    H2CronJobDefinitionStore store

    def setup() {
        DataSource ds = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("classpath:schema.sql")
                .build()
        jdbc = new JdbcTemplate(ds)
        store = new H2CronJobDefinitionStore(jdbc)
    }

    def "save and findById round-trips a definition"() {
        given:
        def cronJob = new CronJob("job1", "Test Job", "default", "0 9 * * *", "UTC",
                "Check status", null, null, true, null, null)
        def definition = new CronJobDefinition(cronJob, "anthropic", "claude-sonnet-4-5",
                "You are a monitor", ToolProfile.CODING, ["web-research", "coding"])

        when:
        store.save(definition)
        def found = store.findById("job1")

        then:
        found.isPresent()
        found.get().cronJob().name() == "Test Job"
        found.get().cronJob().schedule() == "0 9 * * *"
        found.get().provider() == "anthropic"
        found.get().model() == "claude-sonnet-4-5"
        found.get().systemPrompt() == "You are a monitor"
        found.get().toolProfile() == ToolProfile.CODING
        found.get().skills() == ["web-research", "coding"]
    }

    def "save overwrites existing definition (MERGE)"() {
        given:
        def cronJob = new CronJob("job1", "Original", "default", "0 9 * * *", "UTC",
                "prompt", null, null, true, null, null)
        store.save(new CronJobDefinition(cronJob))

        when:
        def updated = new CronJob("job1", "Updated", "default", "0 10 * * *", "UTC",
                "new prompt", null, null, false, null, null)
        store.save(new CronJobDefinition(updated))
        def found = store.findById("job1")

        then:
        found.isPresent()
        found.get().cronJob().name() == "Updated"
        found.get().cronJob().schedule() == "0 10 * * *"
        !found.get().cronJob().enabled()
    }

    def "findAll returns all definitions"() {
        given:
        store.save(new CronJobDefinition(new CronJob("j1", "A", "default", "* * * * *", "UTC",
                "p", null, null, true, null, null)))
        store.save(new CronJobDefinition(new CronJob("j2", "B", "default", "* * * * *", "UTC",
                "p", null, null, false, null, null)))

        when:
        def all = store.findAll()

        then:
        all.size() == 2
    }

    def "findEnabled returns only enabled definitions"() {
        given:
        store.save(new CronJobDefinition(new CronJob("j1", "Enabled", "default", "* * * * *", "UTC",
                "p", null, null, true, null, null)))
        store.save(new CronJobDefinition(new CronJob("j2", "Disabled", "default", "* * * * *", "UTC",
                "p", null, null, false, null, null)))

        when:
        def enabled = store.findEnabled()

        then:
        enabled.size() == 1
        enabled[0].cronJob().name() == "Enabled"
    }

    def "deleteById removes definition"() {
        given:
        store.save(new CronJobDefinition(new CronJob("j1", "A", "default", "* * * * *", "UTC",
                "p", null, null, true, null, null)))

        when:
        def deleted = store.deleteById("j1")

        then:
        deleted
        store.findById("j1").isEmpty()
    }

    def "deleteById returns false for nonexistent id"() {
        expect:
        !store.deleteById("nonexistent")
    }

    def "updateEnabled toggles enabled flag"() {
        given:
        store.save(new CronJobDefinition(new CronJob("j1", "A", "default", "* * * * *", "UTC",
                "p", null, null, true, null, null)))

        when:
        store.updateEnabled("j1", false)
        def found = store.findById("j1")

        then:
        found.isPresent()
        !found.get().cronJob().enabled()
    }

    def "timestamps are persisted correctly"() {
        given:
        def now = Instant.now()
        def cronJob = new CronJob("j1", "Timed", "default", "* * * * *", "UTC",
                "p", null, null, true, now, now.plusSeconds(60))
        store.save(new CronJobDefinition(cronJob))

        when:
        def found = store.findById("j1")

        then:
        found.isPresent()
        found.get().cronJob().lastRunAt().epochSecond == now.epochSecond
        found.get().cronJob().nextRunAt().epochSecond == now.plusSeconds(60).epochSecond
    }
}
