package io.jaiclaw.voicecall.store

import io.jaiclaw.voicecall.model.*
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class JsonlCallStoreSpec extends Specification {

    @TempDir
    Path tempDir

    def "persist and reload from disk"() {
        given:
        // baseDir contains per-tenant subdirectories; SINGLE-mode default-tenant is "default".
        def store = new JsonlCallStore(tempDir)

        def call = new CallRecord("c1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
        call.providerCallId = "CA123"
        call.addTranscriptEntry(TranscriptEntry.Speaker.BOT, "Hello")

        when:
        store.persist(call)
        store.shutdown()
        // Allow async write to complete
        Thread.sleep(200)

        // Reload from disk
        def store2 = new JsonlCallStore(tempDir)
        def history = store2.getHistory(10)

        then:
        history.size() == 1
        history[0].callId == "c1"
        history[0].providerCallId == "CA123"
        history[0].transcript.size() == 1
        // On-disk path is tenant-scoped under the default tenant.
        Files.exists(tempDir.resolve("default").resolve("calls.jsonl"))

        cleanup:
        store2.shutdown()
    }

    def "loadActiveCalls excludes terminal"() {
        given:
        def store = new JsonlCallStore(tempDir)

        def active = new CallRecord("c1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
        active.state = CallState.ACTIVE

        def done = new CallRecord("c2", "twilio", CallDirection.OUTBOUND,
                "+1777", "+1888", CallMode.NOTIFY)
        done.state = CallState.COMPLETED

        when:
        store.persist(active)
        store.persist(done)
        // No tenant context — store returns active records under tenant-scoped keys.
        def activeCalls = store.loadActiveCalls()

        then:
        activeCalls.size() == 1
        activeCalls.containsKey("default:c1")

        cleanup:
        store.shutdown()
    }

    def "handles empty or missing directory gracefully"() {
        given:
        def emptyDir = tempDir.resolve("nonexistent")

        when:
        def store = new JsonlCallStore(emptyDir)

        then:
        store.getHistory(10).isEmpty()
        store.loadActiveCalls().isEmpty()

        cleanup:
        store.shutdown()
    }

    def "migrates a legacy calls.jsonl at the base directory into the default-tenant subdir"() {
        given:
        // Write a pre-tenancy calls.jsonl directly under baseDir.
        Path legacyFile = tempDir.resolve("calls.jsonl")
        Files.createDirectories(tempDir)
        Files.writeString(legacyFile, '{"callId":"legacy-1","provider":"twilio","direction":"OUTBOUND","state":"COMPLETED","from":"+1","to":"+2","mode":"NOTIFY","startedAt":"2026-01-01T00:00:00Z","transcript":[],"processedEventIds":[],"metadata":{}}\n')

        when:
        def store = new JsonlCallStore(tempDir)

        then:
        !Files.exists(legacyFile)
        Files.exists(tempDir.resolve("default").resolve("calls.jsonl"))
        store.getHistory(10)*.callId == ["legacy-1"]

        cleanup:
        store.shutdown()
    }
}
