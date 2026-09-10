package io.jaiclaw.learning.curator

import io.jaiclaw.learning.LearningProperties
import io.jaiclaw.learning.skill.LearnedSkillSidecar
import io.jaiclaw.learning.skill.SkillLifecycle
import io.jaiclaw.learning.skill.SkillWriter
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class CuratorSchedulerSpec extends Specification {

    @TempDir
    Path tmp

    SkillWriter writer

    def setup() {
        writer = new SkillWriter(tmp)
    }

    private LearningProperties props(boolean curatorEnabled = true) {
        new LearningProperties("propose", "balanced", 2, Duration.ZERO, 12000,
                "/tmp/p", tmp.toString(), false, curatorEnabled,
                Duration.ofDays(30), Duration.ofDays(90))
    }

    private void seed(String tenant, String name) {
        writer.write(tenant, name, "d", "b",
                LearnedSkillSidecar.forNewSkill(name, tenant, "sess", "prop"))
    }

    private CuratorScheduler scheduler(Instant at, LearningProperties p = props()) {
        def clock = Clock.fixed(at, ZoneOffset.UTC)
        new CuratorScheduler(new SkillCurator(writer, p, clock), writer, p,
                Duration.ofHours(6), clock)
    }

    def "tenants are discovered from disk, not from configuration"() {
        given:
        seed("acme", "one")
        seed("globex", "two")

        expect: "a tenant no longer configured still gets curated rather than accumulating"
        scheduler(Instant.now()).discoverTenants().toSorted() == ["acme", "globex"]
    }

    def "an empty or missing skills directory discovers nothing"() {
        given:
        def p = new LearningProperties("propose", "balanced", 2, Duration.ZERO, 12000,
                "/tmp/p", tmp.resolve("does-not-exist").toString(), false, true,
                Duration.ofDays(30), Duration.ofDays(90))

        expect:
        scheduler(Instant.now(), p).discoverTenants().isEmpty()
    }

    def "runOnce ages skills across every tenant"() {
        given:
        seed("acme", "old-one")
        seed("globex", "also-old")
        def created = writer.readSidecar("acme", "old-one").get().createdAt()

        when: "31 days later"
        def reports = scheduler(created.plus(Duration.ofDays(31))).runOnce()

        then:
        reports.size() == 2
        reports.every { it.madeStale().size() == 1 }
        writer.readSidecar("acme", "old-one").get().lifecycle() == SkillLifecycle.STALE
        writer.readSidecar("globex", "also-old").get().lifecycle() == SkillLifecycle.STALE
    }

    def "runOnce records when it last ran"() {
        given:
        seed("acme", "x")
        def at = Instant.parse("2026-09-10T12:00:00Z")
        def s = scheduler(at)

        expect:
        s.lastRunAt() == null

        when:
        s.runOnce()

        then:
        s.lastRunAt() == at
    }

    def "a disabled curator never starts"() {
        given:
        def s = scheduler(Instant.now(), props(false))

        when:
        s.start()

        then:
        !s.isRunning()

        cleanup:
        s.close()
    }

    def "start is idempotent and close stops the worker"() {
        given:
        def s = scheduler(Instant.now())

        when:
        s.start()
        s.start()

        then:
        s.isRunning()

        when:
        s.close()

        then:
        !s.isRunning()
    }

    def "the first pass is deferred — starting does not curate immediately"() {
        given: "a skill already old enough to archive"
        seed("acme", "ancient")
        def created = writer.readSidecar("acme", "ancient").get().createdAt()
        def s = scheduler(created.plus(Duration.ofDays(365)))

        when: "the scheduler starts"
        s.start()
        Thread.sleep(120)

        then: "boot does no filesystem curation work; the skill is untouched"
        writer.readSidecar("acme", "ancient").get().lifecycle() == SkillLifecycle.ACTIVE
        s.lastRunAt() == null

        cleanup:
        s.close()
    }

    def "runOnce is safe with no tenants at all"() {
        expect:
        scheduler(Instant.now()).runOnce().isEmpty()
    }
}
