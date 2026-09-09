package io.jaiclaw.tasks

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class TaskExecutorSpec extends Specification {

    @TempDir
    Path tempDir

    JsonFileTaskStore store
    TaskExecutor executor

    def setup() {
        store = new JsonFileTaskStore(tempDir)
        executor = new TaskExecutor(store)
    }

    /**
     * Polls until {@code condition} holds or the timeout expires.
     *
     * The task body's latch fires from *inside* the submitted work, before the
     * executor has persisted the terminal state. A fixed sleep after the latch
     * is therefore a race: it passes on an idle machine and fails under a loaded
     * parallel build. Poll for the state we actually care about instead.
     */
    private boolean await(long timeoutMs = 5_000, Supplier<Boolean> condition) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            if (condition.get()) return true
            Thread.sleep(10)
        }
        return condition.get()
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

        then:
        await { store.findById("t1").get().status() == TaskStatus.SUCCEEDED }
        with(store.findById("t1").get()) {
            status() == TaskStatus.SUCCEEDED
            result() == "completed"
        }
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

        then:
        await { store.findById("t2").get().status() == TaskStatus.FAILED }
        with(store.findById("t2").get()) {
            status() == TaskStatus.FAILED
            error() == "boom"
        }
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

        then: "the RUNNING state is observable while the body is still blocked"
        await { store.findById("t3").get().status() == TaskStatus.RUNNING }
        store.findById("t3").get().status() == TaskStatus.RUNNING

        cleanup:
        completeLatch.countDown()
        // Wait for the virtual thread to finish flushing before @TempDir teardown,
        // otherwise an in-flight save races against directory deletion.
        await { store.findById("t3").get().status() == TaskStatus.SUCCEEDED }
    }
}
