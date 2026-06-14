package io.jaiclaw.agentmind.tendencies.executor

import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StripedDialecticExecutorSpec extends Specification {

    def "same-key submissions run in submission order"() {
        given:
        StripedDialecticExecutor exec = new StripedDialecticExecutor(10)
        List<Integer> observed = new CopyOnWriteArrayList<>()
        CountDownLatch done = new CountDownLatch(5)

        when:
        (1..5).each { i ->
            exec.submit("acme", "u-1", {
                observed << i
                done.countDown()
            })
        }
        done.await(2, TimeUnit.SECONDS)

        then:
        observed == [1, 2, 3, 4, 5]

        cleanup:
        exec.shutdownAndAwait(500)
    }

    def "different-key submissions can run in parallel"() {
        given:
        StripedDialecticExecutor exec = new StripedDialecticExecutor(10)
        CountDownLatch both = new CountDownLatch(2)
        Set<String> ranOn = new java.util.concurrent.ConcurrentHashMap().newKeySet()

        when:
        exec.submit("acme", "u-A", {
            ranOn << "A:" + Thread.currentThread().toString()
            both.countDown()
            both.await(1, TimeUnit.SECONDS)
        })
        exec.submit("acme", "u-B", {
            ranOn << "B:" + Thread.currentThread().toString()
            both.countDown()
            both.await(1, TimeUnit.SECONDS)
        })

        then:
        both.await(2, TimeUnit.SECONDS)

        cleanup:
        exec.shutdownAndAwait(500)
    }

    def "different tenants do not share a stripe even with the same user key"() {
        given:
        StripedDialecticExecutor exec = new StripedDialecticExecutor(10)
        CountDownLatch both = new CountDownLatch(2)

        when:
        exec.submit("tenantA", "shared-u", {
            both.countDown()
            both.await(1, TimeUnit.SECONDS)
        })
        exec.submit("tenantB", "shared-u", {
            both.countDown()
            both.await(1, TimeUnit.SECONDS)
        })

        then:
        both.await(2, TimeUnit.SECONDS)

        cleanup:
        exec.shutdownAndAwait(500)
    }

    def "stats track submitted + dropped + stripes"() {
        given:
        StripedDialecticExecutor exec = new StripedDialecticExecutor(10)

        when:
        exec.submit("acme", "u-1", { Thread.sleep(20) })
        exec.submit("acme", "u-2", { Thread.sleep(20) })
        exec.submit("acme", "u-1", { Thread.sleep(20) })
        Thread.sleep(200)
        StripedDialecticExecutor.Stats s = exec.stats()

        then:
        s.submitted() == 3L
        s.stripes() == 2

        cleanup:
        exec.shutdownAndAwait(500)
    }

    def "queue overflow drops the OLDEST queued task and increments dropped counter"() {
        given:
        // Tiny queue + holding worker so we can fill it deterministically.
        StripedDialecticExecutor exec = new StripedDialecticExecutor(2)
        CountDownLatch hold = new CountDownLatch(1)
        CountDownLatch winnerRan = new CountDownLatch(1)
        List<String> ran = new CopyOnWriteArrayList<>()

        when:
        // Task 1 occupies the single worker thread (waits on hold)
        exec.submit("acme", "u-1", {
            ran << "T1"
            hold.await(2, TimeUnit.SECONDS)
        })
        // Tasks 2, 3 fill the queue (capacity 2)
        exec.submit("acme", "u-1", { ran << "T2" })
        exec.submit("acme", "u-1", { ran << "T3" })

        // Task 4 forces a queue eviction — T2 (oldest queued) is dropped.
        exec.submit("acme", "u-1", {
            ran << "T4"
            winnerRan.countDown()
        })

        // Release the worker so the queue drains.
        hold.countDown()
        winnerRan.await(2, TimeUnit.SECONDS)
        // Give the worker a beat to finish T3 + T4
        Thread.sleep(100)

        then:
        exec.stats().dropped() >= 1L
        ran.contains("T1")
        ran.contains("T3")
        ran.contains("T4")
        !ran.contains("T2")

        cleanup:
        exec.shutdownAndAwait(500)
    }

    def "exception inside a submitted task does NOT corrupt the stripe"() {
        given:
        StripedDialecticExecutor exec = new StripedDialecticExecutor(10)
        CountDownLatch done = new CountDownLatch(1)

        when:
        exec.submit("acme", "u-1", { throw new RuntimeException("boom") })
        exec.submit("acme", "u-1", { done.countDown() })

        then:
        done.await(2, TimeUnit.SECONDS)

        cleanup:
        exec.shutdownAndAwait(500)
    }

    def "rejects invalid queueDepth"() {
        when:
        new StripedDialecticExecutor(0)

        then:
        thrown(IllegalArgumentException)
    }
}
