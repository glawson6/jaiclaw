package io.jaiclaw.core.ops

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * The `bin/jaiclaw pause` fast path writes the ESTOP sentinel in pure bash, with
 * no JVM. These specs pin the wire format both sides share, so a change to
 * either implementation that breaks the other fails here rather than in
 * production during an incident.
 */
class EmergencyStopCliCompatSpec extends Specification {

    @TempDir
    Path tmp

    private EmergencyStop estop() {
        new EmergencyStop(tmp.resolve("ESTOP"))
    }

    def "reads a sentinel written by the bash fast path"() {
        given: "exactly what bin/jaiclaw pause writes"
        Files.writeString(tmp.resolve("ESTOP"),
                '{"reason":"deploying v2","engagedAt":"2026-09-09T16:30:12Z"}')

        when:
        def status = estop().status()

        then:
        status.engaged()
        status.reason() == "deploying v2"
        status.engagedAt() == Instant.parse("2026-09-09T16:30:12Z")
    }

    def "reads a bash-written sentinel with an escaped quote in the reason"() {
        given:
        Files.writeString(tmp.resolve("ESTOP"),
                '{"reason":"deploying \\"v2\\" now","engagedAt":"2026-09-09T16:30:12Z"}')

        when:
        def status = estop().status()

        then:
        status.engaged()
        status.reason() == 'deploying "v2" now'
    }

    def "reads a bash-written sentinel with a null reason"() {
        given: "bin/jaiclaw pause with no reason writes a JSON null"
        Files.writeString(tmp.resolve("ESTOP"),
                '{"reason":null,"engagedAt":"2026-09-09T16:30:12Z"}')

        when:
        def status = estop().status()

        then:
        status.engaged()
        status.reason() == null
        status.engagedAt() != null
    }

    def "the Java writer produces a body the bash reader can cat and a human can read"() {
        given:
        def estop = estop()

        when:
        estop.engage("scheduled maintenance")
        def body = Files.readString(tmp.resolve("ESTOP"))

        then: "single line, both fields, parseable by the same reader"
        body.startsWith('{"reason":"scheduled maintenance"')
        body.contains('"engagedAt":"')
        !body.contains("\n")
        estop.status().reason() == "scheduled maintenance"
    }
}
