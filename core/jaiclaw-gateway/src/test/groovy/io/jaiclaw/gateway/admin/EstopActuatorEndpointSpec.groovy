package io.jaiclaw.gateway.admin

import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.hook.event.EmergencyStopEvent
import io.jaiclaw.core.ops.EmergencyStop
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class EstopActuatorEndpointSpec extends Specification {

    @TempDir
    Path tmp

    EmergencyStop estop
    AgentHookDispatcher hooks = Mock()
    EstopActuatorEndpoint endpoint

    def setup() {
        estop = new EmergencyStop(tmp.resolve("ESTOP"))
        endpoint = new EstopActuatorEndpoint(estop, hooks)
    }

    def "read operation reports a released stop"() {
        when:
        def body = endpoint.status()

        then:
        body.engaged == false
        body.reason == null
        body.sentinel == tmp.resolve("ESTOP").toString()
    }

    def "write engages the stop and fires the event"() {
        when:
        def body = endpoint.set(true, "deploying")

        then:
        body.engaged == true
        body.reason == "deploying"
        estop.isEngaged()
        1 * hooks.fireVoid({ EmergencyStopEvent e -> e.engaged() && e.reason() == "deploying" && e.actor() == "actuator" })
    }

    def "write releases the stop and fires the event"() {
        given:
        estop.engage("paused")

        when:
        def body = endpoint.set(false, null)

        then:
        body.engaged == false
        !estop.isEngaged()
        1 * hooks.fireVoid({ EmergencyStopEvent e -> !e.engaged() && e.actor() == "actuator" })
    }

    def "a bare write with no argument engages rather than silently doing nothing"() {
        when: "an operator POSTs with no body"
        def body = endpoint.set(null, "no explicit flag")

        then: "the safe interpretation is to pause"
        body.engaged == true
        estop.isEngaged()
    }

    def "a failing hook never prevents the operator from pausing"() {
        given:
        hooks.fireVoid(_) >> { throw new IllegalStateException("bad plugin") }

        when:
        def body = endpoint.set(true, "hook explodes")

        then: "the stop still engaged"
        noExceptionThrown()
        body.engaged == true
        estop.isEngaged()
    }

    def "works without a hook dispatcher"() {
        given:
        def bare = new EstopActuatorEndpoint(estop)

        when:
        bare.set(true, "no hooks wired")

        then:
        noExceptionThrown()
        estop.isEngaged()
    }
}
