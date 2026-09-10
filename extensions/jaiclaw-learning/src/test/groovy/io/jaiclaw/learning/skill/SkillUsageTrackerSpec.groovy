package io.jaiclaw.learning.skill

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class SkillUsageTrackerSpec extends Specification {

    @TempDir
    Path tmp

    static final String TENANT = "acme"

    SkillWriter writer
    Instant now = Instant.parse("2026-09-10T12:00:00Z")

    def setup() {
        writer = new SkillWriter(tmp)
    }

    private void seedSkill(String name) {
        writer.write(TENANT, name, "desc", "body",
                LearnedSkillSidecar.forNewSkill(name, TENANT, "sess", "prop-1"))
    }

    private SkillUsageTracker tracker(Duration flushInterval = Duration.ZERO) {
        new SkillUsageTracker(writer, Clock.fixed(now, ZoneOffset.UTC), flushInterval)
    }

    def "recording a use writes lastUsedAt and increments the counter"() {
        given:
        seedSkill("refund-flow")

        expect: "nothing has used it yet"
        writer.readSidecar(TENANT, "refund-flow").get().lastUsedAt() == null
        writer.readSidecar(TENANT, "refund-flow").get().useCount() == 0

        when:
        tracker().recordUse(TENANT, "refund-flow")

        then:
        with(writer.readSidecar(TENANT, "refund-flow").get()) {
            lastUsedAt() == now
            useCount() == 1
        }
    }

    def "multiple skills in one turn are all recorded"() {
        given:
        seedSkill("alpha")
        seedSkill("beta")

        when:
        tracker().recordUse(TENANT, ["alpha", "beta"] as Set)

        then:
        writer.readSidecar(TENANT, "alpha").get().useCount() == 1
        writer.readSidecar(TENANT, "beta").get().useCount() == 1
    }

    def "hits accumulate rather than overwrite"() {
        given:
        seedSkill("popular")
        def t = tracker()

        when:
        3.times { t.recordUse(TENANT, "popular") }

        then:
        writer.readSidecar(TENANT, "popular").get().useCount() == 3
    }

    // ── The bug this fixes ───────────────────────────────────────────────────

    def "a used skill is not archived, while an unused one is"() {
        given: "two skills created at the same moment"
        seedSkill("used-daily")
        seedSkill("never-touched")
        def created = writer.readSidecar(TENANT, "used-daily").get().createdAt()

        and: "one of them is used 89 days later"
        def late = new SkillUsageTracker(writer,
                Clock.fixed(created.plus(Duration.ofDays(89)), ZoneOffset.UTC), Duration.ZERO)
        late.recordUse(TENANT, "used-daily")

        when: "the curator runs at day 91"
        def props = new io.jaiclaw.learning.LearningProperties("propose", "balanced", 2,
                Duration.ZERO, 12000, "/p", "/s", false, true,
                Duration.ofDays(30), Duration.ofDays(90))
        def curator = new io.jaiclaw.learning.curator.SkillCurator(writer, props,
                Clock.fixed(created.plus(Duration.ofDays(91)), ZoneOffset.UTC))
        curator.curate(TENANT)
        curator.curate(TENANT)   // ACTIVE -> STALE -> ARCHIVED needs two passes

        then: "before this tracker existed, BOTH would have aged out on createdAt alone"
        writer.readSidecar(TENANT, "used-daily").get().lifecycle() != SkillLifecycle.ARCHIVED
        writer.readSidecar(TENANT, "never-touched").get().lifecycle() == SkillLifecycle.ARCHIVED
    }

    // ── Hot-path behaviour ───────────────────────────────────────────────────

    def "a flush interval defers the write off the hot path"() {
        given:
        seedSkill("busy")
        def t = tracker(Duration.ofMinutes(5))

        when: "several uses inside one window"
        3.times { t.recordUse(TENANT, "busy") }

        then: "the first write lands, the rest are still pending"
        t.pendingCount() > 0

        when:
        t.flush()

        then: "everything is durable and nothing is pending"
        t.pendingCount() == 0
        writer.readSidecar(TENANT, "busy").get().useCount() == 3
    }

    def "flush is safe when nothing is pending"() {
        expect:
        tracker().flush() == 0
    }

    // ── Robustness ───────────────────────────────────────────────────────────

    def "a skill with no sidecar is skipped, not given one"() {
        when: "recording against a name that was never written by the learning module"
        def t = tracker()
        t.recordUse(TENANT, "hand-authored-skill")
        t.flush()

        then: "inventing a sidecar would claim a hand-authored skill was agent-authored"
        writer.readSidecar(TENANT, "hand-authored-skill").isEmpty()
        noExceptionThrown()
    }

    def "null and blank inputs are ignored"() {
        given:
        def t = tracker()

        when:
        t.recordUse(TENANT, (Set) null)
        t.recordUse(TENANT, [] as Set)
        t.recordUse(TENANT, [null, "", "  "] as Set)

        then:
        noExceptionThrown()
        t.pendingCount() == 0
    }

    def "tenants are tracked separately"() {
        given:
        seedSkill("shared-name")
        writer.write("globex", "shared-name", "d", "b",
                LearnedSkillSidecar.forNewSkill("shared-name", "globex", "s", "p"))

        when:
        tracker().recordUse(TENANT, "shared-name")

        then:
        writer.readSidecar(TENANT, "shared-name").get().useCount() == 1
        writer.readSidecar("globex", "shared-name").get().useCount() == 0
    }

    def "a null tenant is tracked under default"() {
        given:
        writer.write(null, "orphan", "d", "b",
                LearnedSkillSidecar.forNewSkill("orphan", "default", "s", "p"))

        when:
        tracker().recordUse(null, "orphan")

        then:
        writer.readSidecar("default", "orphan").get().useCount() == 1
    }
}
