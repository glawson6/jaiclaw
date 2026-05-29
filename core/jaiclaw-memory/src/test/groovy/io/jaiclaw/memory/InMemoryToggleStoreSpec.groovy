package io.jaiclaw.memory

import spock.lang.Specification

class InMemoryToggleStoreSpec extends Specification {

    def store = new InMemoryToggleStore()

    def "new sessions are enabled by default"() {
        expect:
        store.isEnabled("session-1")
    }

    def "disable marks session as disabled"() {
        when:
        store.disable("session-1")

        then:
        !store.isEnabled("session-1")
    }

    def "enable re-enables a disabled session"() {
        given:
        store.disable("session-1")

        when:
        store.enable("session-1")

        then:
        store.isEnabled("session-1")
    }

    def "disabling one session does not affect others"() {
        when:
        store.disable("session-1")

        then:
        !store.isEnabled("session-1")
        store.isEnabled("session-2")
    }
}
