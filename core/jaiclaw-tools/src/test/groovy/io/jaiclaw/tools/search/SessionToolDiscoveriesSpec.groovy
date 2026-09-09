package io.jaiclaw.tools.search

import spock.lang.Specification

class SessionToolDiscoveriesSpec extends Specification {

    def "discoveries are remembered per session and isolated between sessions"() {
        given:
        def d = new SessionToolDiscoveries()

        when:
        d.discover("sess-a", ["file_read", "file_write"] as Set)
        d.discover("sess-b", ["shell_exec"] as Set)

        then:
        d.discovered("sess-a") == ["file_read", "file_write"] as Set
        d.discovered("sess-b") == ["shell_exec"] as Set
        d.hasDiscovered("sess-a", "file_read")
        !d.hasDiscovered("sess-b", "file_read")
    }

    def "discover reports only the newly added names"() {
        given:
        def d = new SessionToolDiscoveries()
        d.discover("s", ["a"] as Set)

        expect:
        d.discover("s", ["a", "b"] as Set) == ["b"] as Set
    }

    def "an unknown session has discovered nothing"() {
        given:
        def d = new SessionToolDiscoveries()

        expect:
        d.discovered("never-seen").isEmpty()
        !d.hasDiscovered("never-seen", "anything")
    }

    def "null and empty inputs are safe"() {
        given:
        def d = new SessionToolDiscoveries()

        expect:
        d.discover(null, ["a"] as Set).isEmpty()
        d.discover("s", null).isEmpty()
        d.discover("s", [] as Set).isEmpty()
        d.discovered(null).isEmpty()
        !d.hasDiscovered(null, "a")
        !d.hasDiscovered("s", null)
    }

    def "per-session discoveries are bounded"() {
        given: "a cap of 3"
        def d = new SessionToolDiscoveries(3, 100)

        when:
        d.discover("s", ["a", "b", "c", "e", "f"] as Set)

        then: "the session cannot grow without bound"
        d.discovered("s").size() == 3
    }

    def "the session table is bounded and evicts least-recently-used"() {
        given: "room for 2 sessions"
        def d = new SessionToolDiscoveries(64, 2)

        when:
        d.discover("s1", ["a"] as Set)
        d.discover("s2", ["b"] as Set)
        d.discovered("s1")            // touch s1 so s2 is now the eldest
        d.discover("s3", ["c"] as Set)

        then: "a long-lived deployment cannot leak memory through stale sessions"
        d.trackedSessions() == 2
        d.hasDiscovered("s1", "a")
        d.hasDiscovered("s3", "c")
        !d.hasDiscovered("s2", "b")
    }

    def "clear forgets one session, clearAll forgets everything"() {
        given:
        def d = new SessionToolDiscoveries()
        d.discover("s1", ["a"] as Set)
        d.discover("s2", ["b"] as Set)

        when:
        d.clear("s1")

        then:
        d.discovered("s1").isEmpty()
        d.hasDiscovered("s2", "b")

        when:
        d.clearAll()

        then:
        d.trackedSessions() == 0
    }
}
