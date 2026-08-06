package io.jaiclaw.security

import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification

/**
 * Federal compliance (2026-08): FipsPostureStartupCheck acceptance.
 *
 * <p>Notes on test-runtime realism: the JVM's registered JCE providers
 * at test time are the default SunJCE / SunEC / SunJSSE stack, NOT
 * BC-FIPS. This is fine — the "enforcement enabled" test asserts that
 * the check correctly REJECTS the default stack, which is the whole
 * point of the check. The "trust-sun opt-in" test asserts that the
 * escape hatch works.
 */
class FipsPostureStartupCheckSpec extends Specification {

    def "enforcement disabled → no-op (default behavior)"() {
        given:
        def env = new MockEnvironment()
        // fips-enforced NOT set — default to false
        def check = new FipsPostureStartupCheck(env)

        when:
        check.enforce()

        then:
        noExceptionThrown()
    }

    def "enforcement disabled explicitly → no-op"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.effective.fips-enforced", "false")
        def check = new FipsPostureStartupCheck(env)

        when:
        check.enforce()

        then:
        noExceptionThrown()
    }

    def "enforcement enabled + default SunJCE stack → throws IllegalStateException"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.effective.fips-enforced", "true")
        def check = new FipsPostureStartupCheck(env)

        when:
        check.enforce()

        then:
        // The test JVM has default SunJCE / SunEC providers, which are NOT
        // on the FIPS allowlist (BCFIPS or SunPKCS11-*). Expected to throw.
        def e = thrown(IllegalStateException)
        e.message.contains("non-FIPS JCE")
        e.message.contains("BouncyCastle FIPS")   // remediation guidance
    }

    def "enforcement enabled + trust-sun opt-in → passes (attest OS-level FIPS mode)"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.effective.fips-enforced", "true")
        env.setProperty("jaiclaw.compliance.fips.trust-sun", "true")
        def check = new FipsPostureStartupCheck(env)

        when:
        check.enforce()

        then:
        // With trust-sun=true, SUN/SunEC/SunJSSE/SunJCE are treated as FIPS-approved
        // (adopter is attesting they're running under OS-level FIPS mode).
        noExceptionThrown()
    }
}
