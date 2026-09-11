package io.jaiclaw.audit

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TranscriptRetentionSweeperSpec extends Specification {

    @TempDir
    Path tmp

    static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z")

    private void seed(String tenant, LocalDate date, String sessionId) {
        def dir = tmp.resolve(tenant).resolve(date.toString())
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("${sessionId}.json"), '{"sessionId":"' + sessionId + '"}')
    }

    private TranscriptRetentionSweeper sweeper(Duration retention) {
        new TranscriptRetentionSweeper(tmp, retention, Duration.ofHours(12),
                Clock.fixed(NOW, ZoneOffset.UTC))
    }

    private static LocalDate daysAgo(int n) {
        LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(n)
    }

    // ── Default is off ───────────────────────────────────────────────────────

    def "retention is disabled by default and deletes nothing"() {
        given:
        seed("acme", daysAgo(3650), "ancient")

        when:
        def s = TranscriptRetentionSweeper.disabled(tmp)

        then: "deleting an adopter's audit data on upgrade would be worse than the leak"
        !s.isEnabled()
        s.sweep() == 0
        Files.exists(tmp.resolve("acme/${daysAgo(3650)}/ancient.json"))
    }

    def "a null or non-positive retention is treated as disabled"() {
        expect:
        !new TranscriptRetentionSweeper(tmp, retention).isEnabled()

        where:
        retention << [null, Duration.ZERO, Duration.ofDays(-1)]
    }

    def "a disabled sweeper never starts"() {
        given:
        def s = TranscriptRetentionSweeper.disabled(tmp)

        when:
        s.start()

        then:
        !s.isRunning()

        cleanup:
        s.stop()
    }

    // ── Sweeping ─────────────────────────────────────────────────────────────

    def "partitions older than the window are deleted, newer ones kept"() {
        given:
        seed("acme", daysAgo(100), "old")
        seed("acme", daysAgo(10), "recent")

        when:
        def deleted = sweeper(Duration.ofDays(30)).sweep()

        then:
        deleted == 1
        !Files.exists(tmp.resolve("acme/${daysAgo(100)}"))
        Files.exists(tmp.resolve("acme/${daysAgo(10)}/recent.json"))
    }

    def "today's data is never deleted"() {
        given:
        seed("acme", LocalDate.ofInstant(NOW, ZoneOffset.UTC), "today")

        when:
        def deleted = sweeper(Duration.ofDays(1)).sweep()

        then: "a rounding error here would destroy live data"
        deleted == 0
        Files.exists(tmp.resolve("acme/${LocalDate.ofInstant(NOW, ZoneOffset.UTC)}/today.json"))
    }

    def "every tenant is swept"() {
        given:
        seed("acme", daysAgo(100), "a")
        seed("globex", daysAgo(100), "b")
        seed("initech", daysAgo(5), "c")

        when:
        def deleted = sweeper(Duration.ofDays(30)).sweep()

        then:
        deleted == 2
        Files.exists(tmp.resolve("initech/${daysAgo(5)}/c.json"))
    }

    def "multiple transcripts in one partition are all counted"() {
        given:
        seed("acme", daysAgo(100), "one")
        seed("acme", daysAgo(100), "two")
        seed("acme", daysAgo(100), "three")

        expect:
        sweeper(Duration.ofDays(30)).sweep() == 3
    }

    // ── Safety ───────────────────────────────────────────────────────────────

    def "directories that are not ISO dates are left alone"() {
        given: "something an operator or another tool put there"
        Files.createDirectories(tmp.resolve("acme/not-a-date"))
        Files.writeString(tmp.resolve("acme/not-a-date/notes.txt"), "keep me")
        seed("acme", daysAgo(100), "old")

        when:
        def deleted = sweeper(Duration.ofDays(30)).sweep()

        then: "the sweeper only removes partitions it positively understands"
        deleted == 1
        Files.exists(tmp.resolve("acme/not-a-date/notes.txt"))
    }

    def "a missing store directory is not an error"() {
        given:
        def s = new TranscriptRetentionSweeper(tmp.resolve("nope"), Duration.ofDays(30),
                Duration.ofHours(12), Clock.fixed(NOW, ZoneOffset.UTC))

        when:
        def deleted = s.sweep()

        then:
        noExceptionThrown()
        deleted == 0
    }

    def "an empty store sweeps cleanly"() {
        expect:
        sweeper(Duration.ofDays(30)).sweep() == 0
    }

    def "start is idempotent and stop halts the worker"() {
        given:
        def s = sweeper(Duration.ofDays(30))

        when:
        s.start()
        s.start()

        then:
        s.isRunning()

        when:
        s.stop()

        then:
        !s.isRunning()
    }

    def "the first sweep is deferred — starting does not delete immediately"() {
        given:
        seed("acme", daysAgo(100), "old")
        def s = sweeper(Duration.ofDays(30))

        when:
        s.start()
        Thread.sleep(120)

        then: "boot does no deletion work"
        Files.exists(tmp.resolve("acme/${daysAgo(100)}/old.json"))

        cleanup:
        s.stop()
    }
}
