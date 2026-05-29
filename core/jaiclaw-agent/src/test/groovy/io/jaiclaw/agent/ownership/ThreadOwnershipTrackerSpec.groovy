package io.jaiclaw.agent.ownership

import spock.lang.Specification

import java.time.Duration

class ThreadOwnershipTrackerSpec extends Specification {

    def tracker = new ThreadOwnershipTracker(Duration.ofHours(1))

    def "claim succeeds on unowned thread"() {
        expect:
        tracker.claim("thread-1", "agent-a")
        tracker.getOwner("thread-1").get() == "agent-a"
    }

    def "claim fails when thread is owned by another agent"() {
        given:
        tracker.claim("thread-1", "agent-a")

        expect:
        !tracker.claim("thread-1", "agent-b")
        tracker.getOwner("thread-1").get() == "agent-a"
    }

    def "claim succeeds when existing ownership has expired"() {
        given:
        tracker = new ThreadOwnershipTracker(Duration.ofMillis(1))
        tracker.claim("thread-1", "agent-a")
        Thread.sleep(10)

        expect:
        tracker.claim("thread-1", "agent-b")
        tracker.getOwner("thread-1").get() == "agent-b"
    }

    def "forceAssign overrides existing ownership"() {
        given:
        tracker.claim("thread-1", "agent-a")

        when:
        tracker.forceAssign("thread-1", "agent-b")

        then:
        tracker.getOwner("thread-1").get() == "agent-b"
    }

    def "release removes ownership"() {
        given:
        tracker.claim("thread-1", "agent-a")

        when:
        tracker.release("thread-1")

        then:
        tracker.getOwner("thread-1").isEmpty()
    }
}
