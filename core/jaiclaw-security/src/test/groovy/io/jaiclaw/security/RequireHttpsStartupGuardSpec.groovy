package io.jaiclaw.security

import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification

/**
 * T1-7 acceptance: require-https guard throws at startup on non-loopback +
 * plaintext binds when enabled, is a no-op otherwise.
 */
class RequireHttpsStartupGuardSpec extends Specification {

    private static JaiClawSecurityProperties propsRequiring(boolean requireHttps) {
        JaiClawSecurityProperties.builder().requireHttps(requireHttps).build()
    }

    def "guard disabled → no-op regardless of bind or TLS"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("server.address", "0.0.0.0")   // public bind, no TLS
        def guard = new RequireHttpsStartupGuard(env, propsRequiring(false))

        when:
        guard.enforce()

        then:
        noExceptionThrown()
    }

    def "guard enabled + SSL enabled → allowed"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("server.address", "0.0.0.0")
        env.setProperty("server.ssl.enabled", "true")
        def guard = new RequireHttpsStartupGuard(env, propsRequiring(true))

        when:
        guard.enforce()

        then:
        noExceptionThrown()
    }

    def "guard enabled + SSL keystore configured → allowed (Spring Boot idiom)"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("server.address", "0.0.0.0")
        env.setProperty("server.ssl.key-store", "/etc/ssl/keystore.p12")
        def guard = new RequireHttpsStartupGuard(env, propsRequiring(true))

        when:
        guard.enforce()

        then:
        noExceptionThrown()
    }

    def "guard enabled + loopback bind → allowed (dev workflow)"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("server.address", loopbackAddr)
        def guard = new RequireHttpsStartupGuard(env, propsRequiring(true))

        when:
        guard.enforce()

        then:
        noExceptionThrown()

        where:
        loopbackAddr << ["127.0.0.1", "::1", "localhost", "0:0:0:0:0:0:0:1"]
    }

    def "guard enabled + non-loopback bind + no TLS → throws IllegalStateException"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("server.address", bindAddr)
        def guard = new RequireHttpsStartupGuard(env, propsRequiring(true))

        when:
        guard.enforce()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains(bindAddr)
        e.message.contains("require-https=true")

        where:
        bindAddr << ["0.0.0.0", "10.0.1.5", "192.168.1.100", "203.0.113.7"]
    }

    def "guard enabled + no bind address set → throws (default is bind-all)"() {
        // Spring's default for server.address is unset which means "bind to
        // all interfaces". That's public — the guard should throw.
        given:
        def env = new MockEnvironment()   // no server.address set
        def guard = new RequireHttpsStartupGuard(env, propsRequiring(true))

        when:
        guard.enforce()

        then:
        thrown(IllegalStateException)
    }
}
