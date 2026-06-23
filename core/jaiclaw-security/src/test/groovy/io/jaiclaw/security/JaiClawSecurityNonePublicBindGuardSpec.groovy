package io.jaiclaw.security

import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import spock.lang.Specification

/**
 * Verifies the 0.9.2 fail-fast guard on
 * {@code jaiclaw.security.mode=none} bound to a non-loopback
 * interface.
 *
 * <p>The contract:
 * <ul>
 *   <li>Bound to 127.0.0.1 / localhost / ::1 / 0:0:0:0:0:0:0:1 — start
 *       cleanly with an INFO log.</li>
 *   <li>Bound to anything else (including the empty default
 *       {@code server.address}) — throw at startup unless the operator
 *       has set {@code jaiclaw.security.allow-none-on-public-bind=true}
 *       as a waiver.</li>
 * </ul>
 */
class JaiClawSecurityNonePublicBindGuardSpec extends Specification {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SecurityAutoConfiguration,
                    JaiClawSecurityAutoConfiguration))

    def "starts cleanly when bound to loopback: '#bind'"() {
        when:
        runner.withPropertyValues(
                "jaiclaw.security.mode=none",
                "server.address=${bind}").run { ctx ->
            assert ctx.startupFailure == null
        }

        then:
        noExceptionThrown()

        where:
        bind << ["127.0.0.1", "localhost", "::1", "0:0:0:0:0:0:0:1"]
    }

    def "fails fast on empty bind (all interfaces) without waiver"() {
        when:
        runner.withPropertyValues("jaiclaw.security.mode=none").run { ctx ->
            assert ctx.startupFailure != null
            assert rootCauseMessage(ctx.startupFailure).contains("Refusing to start")
            assert rootCauseMessage(ctx.startupFailure).contains("allow-none-on-public-bind")
        }

        then:
        noExceptionThrown()
    }

    def "fails fast on non-loopback bind without waiver"() {
        when:
        runner.withPropertyValues(
                "jaiclaw.security.mode=none",
                "server.address=10.0.0.1").run { ctx ->
            assert ctx.startupFailure != null
            assert rootCauseMessage(ctx.startupFailure).contains("Refusing to start")
            assert rootCauseMessage(ctx.startupFailure).contains("10.0.0.1")
        }

        then:
        noExceptionThrown()
    }

    def "waiver flag permits non-loopback bind (with WARN log)"() {
        when:
        runner.withPropertyValues(
                "jaiclaw.security.mode=none",
                "server.address=10.0.0.1",
                "jaiclaw.security.allow-none-on-public-bind=true").run { ctx ->
            assert ctx.startupFailure == null
        }

        then:
        noExceptionThrown()
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t
        while (cur.cause != null && cur.cause != cur) {
            cur = cur.cause
        }
        return cur.message ?: ""
    }
}
