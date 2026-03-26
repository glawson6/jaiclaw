package io.jaiclaw.tools.security

import spock.lang.Specification
import spock.lang.Subject

class HandshakeSessionStoreSpec extends Specification {

    @Subject
    def store = new HandshakeSessionStore()

    def "create() produces a session with unique ID"() {
        when:
        def s1 = store.create()
        def s2 = store.create()

        then:
        s1.handshakeId != null
        s2.handshakeId != null
        s1.handshakeId != s2.handshakeId
    }

    def "get() returns created session"() {
        given:
        def session = store.create()

        expect:
        store.get(session.handshakeId).isPresent()
        store.get(session.handshakeId).get().is(session)
    }

    def "get() returns empty for unknown ID"() {
        expect:
        store.get("nonexistent").isEmpty()
    }

    def "require() returns created session"() {
        given:
        def session = store.create()

        expect:
        store.require(session.handshakeId).is(session)
    }

    def "require() throws for unknown ID"() {
        when:
        store.require("nonexistent")

        then:
        thrown(IllegalArgumentException)
    }

    def "remove() deletes session"() {
        given:
        def session = store.create()

        when:
        def removed = store.remove(session.handshakeId)

        then:
        removed
        store.get(session.handshakeId).isEmpty()
    }

    def "remove() returns false for unknown ID"() {
        expect:
        !store.remove("nonexistent")
    }

    def "size() tracks active sessions"() {
        expect:
        store.size() == 0

        when:
        def s1 = store.create()
        def s2 = store.create()

        then:
        store.size() == 2

        when:
        store.remove(s1.handshakeId)

        then:
        store.size() == 1
    }

    def "session state is mutable across phases"() {
        given:
        def session = store.create()

        when:
        session.selectedCipherSuite = "ECDH-P256-AES128-GCM-SHA256"
        session.selectedAuthMethod = "HMAC"
        session.identityVerified = true
        session.completed = true

        then:
        def retrieved = store.require(session.handshakeId)
        retrieved.selectedCipherSuite == "ECDH-P256-AES128-GCM-SHA256"
        retrieved.selectedAuthMethod == "HMAC"
        retrieved.identityVerified
        retrieved.completed
    }
}
