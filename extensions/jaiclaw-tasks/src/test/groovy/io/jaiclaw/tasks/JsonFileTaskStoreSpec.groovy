package io.jaiclaw.tasks

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant

class JsonFileTaskStoreSpec extends Specification {

    @TempDir
    Path tempDir

    JsonFileTaskStore store

    def setup() {
        store = new JsonFileTaskStore(tempDir)
    }

    def "save and find by id"() {
        given:
        def task = new TaskRecord("t1", "Test Task", "desc", TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null)

        when:
        store.save(task)

        then:
        store.findById("t1").isPresent()
        store.findById("t1").get().name() == "Test Task"
    }

    def "find by status"() {
        given:
        store.save(new TaskRecord("t1", "T1", null, TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null))
        store.save(new TaskRecord("t2", "T2", null, TaskStatus.RUNNING,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null))

        expect:
        store.findByStatus(TaskStatus.QUEUED).size() == 1
        store.findByStatus(TaskStatus.RUNNING).size() == 1
    }

    def "delete removes task"() {
        given:
        store.save(new TaskRecord("t1", "T1", null, TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null))

        when:
        store.deleteById("t1")

        then:
        store.findById("t1").isEmpty()
    }

    def "persists to disk and reloads"() {
        given:
        store.save(new TaskRecord("t1", "Persisted", null, TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null))

        when:
        def store2 = new JsonFileTaskStore(tempDir)

        then:
        store2.findById("t1").isPresent()
        store2.findById("t1").get().name() == "Persisted"
    }

    def "count returns number of tasks"() {
        given:
        store.save(new TaskRecord("t1", "T1", null, TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null))
        store.save(new TaskRecord("t2", "T2", null, TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null))

        expect:
        store.count() == 2
    }
}
