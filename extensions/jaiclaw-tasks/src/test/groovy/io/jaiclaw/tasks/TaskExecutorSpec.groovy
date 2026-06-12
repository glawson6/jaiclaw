package io.jaiclaw.tasks

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TaskExecutorSpec extends Specification {

    @TempDir
    Path tempDir

    JsonFileTaskStore store
    TaskExecutor executor

    def setup() {
        store = new JsonFileTaskStore(tempDir)
        executor = new TaskExecutor(store)
    }

    def "successful execution updates task to SUCCEEDED"() {
        given:
        def task = new TaskRecord("t1", "Test", "desc", TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null)
        store.save(task)
        def latch = new CountDownLatch(1)

        when:
        executor.submit(task, { t ->
            latch.countDown()
            return "completed"
        })
        latch.await(5, TimeUnit.SECONDS)
        Thread.sleep(100) // allow store flush

        then:
        def result = store.findById("t1")
        result.isPresent()
        result.get().status() == TaskStatus.SUCCEEDED
        result.get().result() == "completed"
    }

    def "failed execution updates task to FAILED"() {
        given:
        def task = new TaskRecord("t2", "Failing", "desc", TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null)
        store.save(task)
        def latch = new CountDownLatch(1)

        when:
        executor.submit(task, { t ->
            latch.countDown()
            throw new RuntimeException("boom")
        })
        latch.await(5, TimeUnit.SECONDS)
        Thread.sleep(100) // allow store flush

        then:
        def result = store.findById("t2")
        result.isPresent()
        result.get().status() == TaskStatus.FAILED
        result.get().error() == "boom"
    }

    def "task is marked RUNNING during execution"() {
        given:
        def task = new TaskRecord("t3", "Running", "desc", TaskStatus.QUEUED,
                TaskDeliveryState.PENDING, null, null, null, Map.of(),
                Instant.now(), null, null, null)
        store.save(task)
        def runningLatch = new CountDownLatch(1)
        def completeLatch = new CountDownLatch(1)

        when:
        executor.submit(task, { t ->
            runningLatch.countDown()
            completeLatch.await(5, TimeUnit.SECONDS)
            return "done"
        })
        runningLatch.await(5, TimeUnit.SECONDS)
        Thread.sleep(50)

        then:
        store.findById("t3").get().status() == TaskStatus.RUNNING

        cleanup:
        completeLatch.countDown()
        // Wait for the virtual thread to finish flushing before @TempDir teardown,
        // otherwise an in-flight save races against directory deletion.
        for (int i = 0; i < 50; i++) {
            if (store.findById("t3").get().status() == TaskStatus.SUCCEEDED) break
            Thread.sleep(20)
        }
    }
}
