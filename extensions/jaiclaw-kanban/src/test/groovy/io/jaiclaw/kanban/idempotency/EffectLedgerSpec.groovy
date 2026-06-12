package io.jaiclaw.kanban.idempotency

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class EffectLedgerSpec extends Specification {

    @TempDir
    Path tempDir

    def "record then lookup returns the stored result"() {
        given:
        def ledger = new EffectLedger(tempDir)

        when:
        ledger.record("k1", "the result")

        then:
        ledger.lookup("k1").orElse(null) == "the result"
        ledger.lookup("missing").isEmpty()
    }

    def "records survive across a restart via the jsonl journal"() {
        given:
        def a = new EffectLedger(tempDir)
        a.record("k1", "alpha")
        a.record("k2", "beta")

        when:
        def b = new EffectLedger(tempDir)

        then:
        b.lookup("k1").get() == "alpha"
        b.lookup("k2").get() == "beta"
        b.size() == 2
    }

    def "re-recording the same key+value is a no-op (no duplicate jsonl line)"() {
        given:
        def ledger = new EffectLedger(tempDir)
        ledger.record("k1", "same")
        long after = Files.size(tempDir.resolve("effects.jsonl"))

        when:
        ledger.record("k1", "same")

        then:
        Files.size(tempDir.resolve("effects.jsonl")) == after
    }

    def "re-recording with a different value appends a new line and overwrites in-memory"() {
        given:
        def ledger = new EffectLedger(tempDir)
        ledger.record("k1", "v1")

        when:
        ledger.record("k1", "v2")

        then:
        ledger.lookup("k1").get() == "v2"
        Files.readAllLines(tempDir.resolve("effects.jsonl")).size() == 2
    }

    def "null key on record is a no-op"() {
        given:
        def ledger = new EffectLedger(tempDir)

        when:
        ledger.record(null, "ignored")

        then:
        ledger.size() == 0
    }

    def "malformed jsonl lines are skipped on load"() {
        given:
        Files.writeString(tempDir.resolve("effects.jsonl"),
                """{"key":"k1","result":"good","recordedAt":"2026-06-12T00:00:00Z"}
                   {not json
                   {"key":"k2","result":"also good","recordedAt":"2026-06-12T00:00:01Z"}
                """.stripIndent())

        when:
        def ledger = new EffectLedger(tempDir)

        then:
        ledger.lookup("k1").get() == "good"
        ledger.lookup("k2").get() == "also good"
        ledger.size() == 2
    }
}
