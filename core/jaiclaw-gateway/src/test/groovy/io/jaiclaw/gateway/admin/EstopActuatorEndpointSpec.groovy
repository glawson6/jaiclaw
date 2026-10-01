package io.jaiclaw.gateway.admin

import io.jaiclaw.core.agent.AgentHookDispatcher
import io.jaiclaw.core.hook.event.EmergencyStopEvent
import io.jaiclaw.core.ops.EmergencyStop
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class EstopActuatorEndpointSpec extends Specification {

    @TempDir
    Path tmp

    EmergencyStop estop
    AgentHookDispatcher hooks = Mock()
    EstopActuatorEndpoint endpoint

    static final String ROLE = "JAICLAW_OPERATOR"

    def setup() {
        estop = new EmergencyStop(tmp.resolve("ESTOP"))
        // 1.3.0: the write operation is role-guarded and a blank role DENIES, so
        // the endpoint must be given a role and the caller must hold it. The
        // read operation is unaffected.
        endpoint = new EstopActuatorEndpoint(estop, hooks,
                new EstopAuthzProperties(true, ROLE))
        authenticateWith(ROLE)
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    private static void authenticateWith(String... authorities) {
        SecurityContextHolder.context.authentication =
                new UsernamePasswordAuthenticationToken("operator", "n/a",
                        authorities.collect { new SimpleGrantedAuthority(it) })
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
        given: "the 1-arg constructor defaults the role to blank, which now denies,"
        and: "so this exercises the no-hooks path with authorization satisfied"
        def bare = new EstopActuatorEndpoint(estop, null,
                new EstopAuthzProperties(true, ROLE))

        when:
        bare.set(true, "no hooks wired")

        then:
        noExceptionThrown()
        estop.isEngaged()
    }

    // --- 1.3.0: authorization on the write operation ---

    def "the write operation denies when no role is configured"() {
        given: "a blank role means deny here, unlike the admin endpoints' allow-all default"
        def open = new EstopActuatorEndpoint(estop, hooks, EstopAuthzProperties.defaults())

        when:
        open.set(true, "attempt")

        then:
        def e = thrown(EstopActuatorEndpoint.EstopAuthorizationException)
        e.message.contains("role is not set")

        and: "and the fleet was not paused"
        !estop.isEngaged()
    }

    def "the write operation denies a caller without the role"() {
        given:
        authenticateWith("SOME_OTHER_ROLE")

        when:
        endpoint.set(true, "attempt")

        then:
        thrown(EstopActuatorEndpoint.EstopAuthorizationException)
        !estop.isEngaged()
    }

    def "the write operation denies an unauthenticated caller"() {
        given: "mode=none leaves the filter chain permitAll, so this is reachable"
        SecurityContextHolder.clearContext()

        when:
        endpoint.set(true, "attempt")

        then:
        thrown(EstopActuatorEndpoint.EstopAuthorizationException)
        !estop.isEngaged()
    }

    def "a release attempt is guarded too, not just engage"() {
        given: "releasing someone else's pause is also a privileged action"
        estop.engage("set by an operator")
        authenticateWith("SOME_OTHER_ROLE")

        when:
        endpoint.set(false, null)

        then:
        thrown(EstopActuatorEndpoint.EstopAuthorizationException)
        estop.isEngaged()
    }

    def "the read operation stays unguarded"() {
        given: "status discloses no secret and operators need it when debugging"
        SecurityContextHolder.clearContext()

        when:
        def body = endpoint.status()

        then:
        noExceptionThrown()
        body.engaged == false
    }

    def "hasRole distinguishes configured from blank"() {
        expect:
        !EstopAuthzProperties.defaults().hasRole()
        !new EstopAuthzProperties(true, "   ").hasRole()
        new EstopAuthzProperties(true, ROLE).hasRole()
    }
}
