package io.jaiclaw.core.ops

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class EmergencyStopSpec extends Specification {

    @TempDir
    Path tmp

    private EmergencyStop estop() {
        new EmergencyStop(tmp.resolve("ESTOP"))
    }

    def "absent sentinel means running"() {
        given:
        def estop = estop()

        expect:
        !estop.isEngaged()
        !estop.status().engaged()
        estop.status().reason() == null
    }

    def "engage creates the sentinel and records the reason"() {
        given:
        def estop = estop()

        when:
        estop.engage("deploying v2")

        then:
        estop.isEngaged()
        with(estop.status()) {
            engaged()
            reason() == "deploying v2"
            engagedAt() != null
        }
    }

    def "release removes the sentinel and is idempotent"() {
        given:
        def estop = estop()
        estop.engage("pausing")

        when:
        def removed = estop.release()

        then:
        removed
        !estop.isEngaged()

        when: "releasing again"
        def removedAgain = estop.release()

        then: "no error, just nothing to do"
        !removedAgain
        !estop.isEngaged()
    }

    def "engage is idempotent and rewrites the reason"() {
        given:
        def estop = estop()

        when:
        estop.engage("first")
        estop.engage("second")

        then:
        estop.status().reason() == "second"
    }

    def "a corrupt sentinel still counts as engaged — fail safe"() {
        given: "a sentinel containing garbage rather than JSON"
        def estop = estop()
        Files.writeString(tmp.resolve("ESTOP"), "}{ not json at all")

        expect: "the stop holds; only the reason is unavailable"
        estop.isEngaged()
        estop.status().engaged()
        estop.status().reason() == null
    }

    def "an empty sentinel still counts as engaged"() {
        given: "the shape an operator gets from `touch ESTOP`"
        def estop = estop()
        Files.writeString(tmp.resolve("ESTOP"), "")

        expect:
        estop.isEngaged()
        estop.status().engaged()
        estop.status().engagedAt() != null
    }

    def "a null reason is written and read back as null"() {
        given:
        def estop = estop()

        when:
        estop.engage(null)

        then:
        estop.isEngaged()
        estop.status().reason() == null
    }

    def "reasons containing quotes and newlines round-trip"() {
        given:
        def estop = estop()
        def reason = 'incident "P1"\nrolling back\tnow'

        when:
        estop.engage(reason)

        then:
        estop.status().reason() == reason
    }

    def "engage creates missing parent directories"() {
        given:
        def nested = tmp.resolve("a/b/c/ESTOP")
        def estop = new EmergencyStop(nested)

        when:
        estop.engage("nested")

        then:
        Files.exists(nested)
        estop.isEngaged()
    }

    def "the default sentinel path honours the jaiclaw.home system property"() {
        given:
        def previous = System.getProperty(EmergencyStop.HOME_PROPERTY)
        System.setProperty(EmergencyStop.HOME_PROPERTY, tmp.toString())

        when:
        def path = EmergencyStop.defaultSentinelPath()

        then:
        path == tmp.resolve("ESTOP")

        cleanup:
        if (previous == null) System.clearProperty(EmergencyStop.HOME_PROPERTY)
        else System.setProperty(EmergencyStop.HOME_PROPERTY, previous)
    }

    def "reasonIfPresent exposes the reason as an Optional"() {
        given:
        def estop = estop()
        estop.engage("maintenance")

        expect:
        estop.status().reasonIfPresent().isPresent()
        estop.status().reasonIfPresent().get() == "maintenance"
    }
}
