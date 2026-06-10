package io.jaiclaw.voicecall.store

import io.jaiclaw.voicecall.model.*
import spock.lang.Specification

class InMemoryCallStoreSpec extends Specification {

    def store = new InMemoryCallStore()

    def "persist and retrieve calls"() {
        given:
        def call1 = new CallRecord("c1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
        def call2 = new CallRecord("c2", "twilio", CallDirection.INBOUND,
                "+1777", "+1888", CallMode.NOTIFY)

        when:
        store.persist(call1)
        store.persist(call2)

        then:
        store.size() == 2
    }

    def "loadActiveCalls excludes terminal calls"() {
        given:
        def active = new CallRecord("c1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
        active.state = CallState.ACTIVE

        def completed = new CallRecord("c2", "twilio", CallDirection.OUTBOUND,
                "+1777", "+1888", CallMode.NOTIFY)
        completed.state = CallState.COMPLETED

        store.persist(active)
        store.persist(completed)

        when:
        // No tenant context set — store returns active records with full
        // tenant-scoped keys ("default:c1" in SINGLE mode).
        def activeCalls = store.loadActiveCalls()

        then:
        activeCalls.size() == 1
        activeCalls.containsKey("default:c1")
        !activeCalls.containsKey("default:c2")
    }

    def "getHistory returns calls sorted by start time descending"() {
        given:
        def call1 = new CallRecord("c1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
        def call2 = new CallRecord("c2", "twilio", CallDirection.OUTBOUND,
                "+1777", "+1888", CallMode.CONVERSATION)

        // Ensure different timestamps
        Thread.sleep(10)
        store.persist(call1)
        Thread.sleep(10)
        store.persist(call2)

        when:
        def history = store.getHistory(10)

        then:
        history.size() == 2
        // Most recent first
        history[0].callId == "c2"
        history[1].callId == "c1"
    }

    def "getHistory respects limit"() {
        given:
        (1..5).each { i ->
            store.persist(new CallRecord("c$i", "twilio", CallDirection.OUTBOUND,
                    "+1555", "+1666", CallMode.CONVERSATION))
        }

        when:
        def history = store.getHistory(3)

        then:
        history.size() == 3
    }

    def "concurrent access is safe"() {
        given:
        def threads = (1..10).collect { i ->
            Thread.start {
                def call = new CallRecord("c$i", "twilio", CallDirection.OUTBOUND,
                        "+1555", "+1666", CallMode.CONVERSATION)
                store.persist(call)
            }
        }

        when:
        threads*.join()

        then:
        store.size() == 10
    }
}
