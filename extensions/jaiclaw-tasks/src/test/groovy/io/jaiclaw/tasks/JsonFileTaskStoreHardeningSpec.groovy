package io.jaiclaw.tasks

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Targets the JsonFileTaskStore hardening landed alongside the kanban work:
 * atomic flush (tmp + ATOMIC_MOVE), corrupt-file quarantine + fail-fast, and
 * optimistic compareAndSave.
 */
class JsonFileTaskStoreHardeningSpec extends Specification {

    @TempDir
    Path tempDir

    private TaskRecord makeTask(String id, long version = 0L) {
        new TaskRecord(id, id.toUpperCase(), null,
                TaskStatus.QUEUED, TaskDeliveryState.PENDING,
                null, null, null, Map.of(), Instant.now(), null, null, null,
                null, null, null, version, 0, null)
    }

    def "save writes via a tmp file then atomic-move promotes it"() {
        given:
        def store = new JsonFileTaskStore(tempDir)

        when:
        store.save(makeTask("a1"))

        then:
        Files.exists(tempDir.resolve("tasks.json"))
        !Files.exists(tempDir.resolve("tasks.json.tmp"))
    }

    def "corrupt task store is quarantined and startup fails fast by default"() {
        given:
        Files.writeString(tempDir.resolve("tasks.json"), "{not valid json")

        when:
        new JsonFileTaskStore(tempDir)

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("Refusing to start")
        def quarantined = Files.list(tempDir)
                .filter(p -> p.fileName.toString().startsWith("tasks.json.corrupt-"))
                .toList()
        quarantined.size() == 1
    }

    def "ignore-corrupt flag allows starting empty after quarantining"() {
        given:
        Files.writeString(tempDir.resolve("tasks.json"), "{not valid json")

        when:
        def store = new JsonFileTaskStore(tempDir,
                new TenantGuard(TenantProperties.DEFAULT), true)

        then:
        store.count() == 0L
        Files.list(tempDir)
                .filter(p -> p.fileName.toString().startsWith("tasks.json.corrupt-"))
                .count() == 1L
    }

    def "compareAndSave bumps version and rejects stale writers"() {
        given:
        def store = new JsonFileTaskStore(tempDir)
        store.save(makeTask("c1"))           // version 0 in store
        def stored = store.findById("c1").get()

        when: "first writer goes through CAS with expected=0"
        def first = store.compareAndSave(stored)

        then:
        first.isPresent()
        first.get().version() == 1L

        when: "second writer still holds the stale version=0 record"
        def second = store.compareAndSave(stored)

        then:
        second.isEmpty()
        store.findById("c1").get().version() == 1L
    }

    def "compareAndSave on a new id (no prior record) accepts version=0 and writes version=1"() {
        given:
        def store = new JsonFileTaskStore(tempDir)

        when:
        def result = store.compareAndSave(makeTask("fresh", 0L))

        then:
        result.isPresent()
        result.get().version() == 1L
        store.findById("fresh").get().version() == 1L
    }
}
