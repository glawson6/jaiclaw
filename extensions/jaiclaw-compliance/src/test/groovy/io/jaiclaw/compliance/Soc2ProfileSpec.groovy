package io.jaiclaw.compliance

import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification

/**
 * The soc2 profile bundle, and the invariants that protect every other
 * deployment from it.
 */
class Soc2ProfileSpec extends Specification {

    ComplianceEnvironmentPostProcessor pp = new ComplianceEnvironmentPostProcessor()

    def "soc2 sets the five effective flags it is responsible for"() {
        given:
        def env = new MockEnvironment().withProperty("jaiclaw.compliance.profile", "soc2")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        env.getProperty("jaiclaw.compliance.effective.profile") == "soc2"
        env.getProperty("jaiclaw.compliance.effective.require-https", Boolean)
        env.getProperty("jaiclaw.compliance.effective.retention-enforcement", Boolean)
        env.getProperty("jaiclaw.compliance.effective.audit-chat-client", Boolean)
        env.getProperty("jaiclaw.compliance.effective.audit-hash-chain", Boolean)
        env.getProperty("jaiclaw.compliance.effective.encrypt-at-rest", Boolean)
    }

    def "soc2 sets the cross-subsystem security properties"() {
        given:
        def env = new MockEnvironment().withProperty("jaiclaw.compliance.profile", "soc2")

        when:
        pp.postProcessEnvironment(env, null)

        then: "CC6.3 — the framework default is FULL, which fails open"
        env.getProperty("jaiclaw.security.default-tool-profile") == "MINIMAL"

        and: "CC6.6"
        env.getProperty("jaiclaw.security.rate-limit.enabled", Boolean)

        and: "CC6.7"
        env.getProperty("jaiclaw.security.require-https", Boolean)
    }

    def "soc2 does NOT set regime-specific flags"() {
        given:
        def env = new MockEnvironment().withProperty("jaiclaw.compliance.profile", "soc2")

        when:
        pp.postProcessEnvironment(env, null)

        then: "BAA is HIPAA-specific; FIPS/FedRAMP/CUI are federal"
        !env.getProperty("jaiclaw.compliance.effective.baa-warnings", Boolean)
        !env.getProperty("jaiclaw.compliance.effective.fips-enforced", Boolean)
        !env.getProperty("jaiclaw.compliance.effective.fedramp-warnings", Boolean)
        !env.getProperty("jaiclaw.compliance.effective.cui-warnings", Boolean)
    }

    def "soc2 does NOT claim prompt redaction"() {
        given:
        def env = new MockEnvironment().withProperty("jaiclaw.compliance.profile", "soc2")

        when:
        pp.postProcessEnvironment(env, null)

        then: "RegexPromptRedactor has no framework call site and is wired non-strict, so enabling it would imply a control that does not operate"
        !env.getProperty("jaiclaw.compliance.effective.prompt-redaction", Boolean)
    }

    def "an explicit operator value is never overwritten by the profile"() {
        given: "an operator who has deliberately chosen FULL"
        def env = new MockEnvironment()
                .withProperty("jaiclaw.compliance.profile", "soc2")
                .withProperty("jaiclaw.security.default-tool-profile", "FULL")

        when:
        pp.postProcessEnvironment(env, null)

        then: "a profile is a bundle of defaults, not an override of an instruction"
        env.getProperty("jaiclaw.security.default-tool-profile") == "FULL"
    }

    def "an explicit false disables a flag the profile would have set"() {
        given:
        def env = new MockEnvironment()
                .withProperty("jaiclaw.compliance.profile", "soc2")
                .withProperty("jaiclaw.compliance.encrypt-at-rest", "false")

        when:
        pp.postProcessEnvironment(env, null)

        then: "opting out of one control must not require abandoning the profile"
        !env.getProperty("jaiclaw.compliance.effective.encrypt-at-rest", Boolean)

        and: "the rest of the bundle is unaffected"
        env.getProperty("jaiclaw.compliance.effective.audit-hash-chain", Boolean)
    }

    def "individual flags work with no profile at all"() {
        given: "someone who wants only the audit chain"
        def env = new MockEnvironment()
                .withProperty("jaiclaw.compliance.audit-hash-chain", "true")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        env.getProperty("jaiclaw.compliance.effective.audit-hash-chain", Boolean)

        and: "and nothing else comes with it"
        !env.getProperty("jaiclaw.compliance.effective.encrypt-at-rest", Boolean)
        env.getProperty("jaiclaw.security.default-tool-profile") == null
    }

    def "kebab and underscore spellings both resolve"() {
        expect:
        ["soc2", "SOC2", "Soc2"].every { spelling ->
            def env = new MockEnvironment().withProperty("jaiclaw.compliance.profile", spelling)
            pp.postProcessEnvironment(env, null)
            env.getProperty("jaiclaw.compliance.effective.profile") == "soc2"
        }
    }

    // ---- the invariant that protects every existing deployment ----

    def "DEFAULT profile changes nothing — no flags, no cross-subsystem writes"() {
        given: "the default: no compliance configuration at all"
        def env = new MockEnvironment()

        when:
        pp.postProcessEnvironment(env, null)

        then: "every effective flag is false"
        env.getProperty("jaiclaw.compliance.effective.profile") == "none"
        !env.getProperty("jaiclaw.compliance.effective.audit-hash-chain", Boolean)
        !env.getProperty("jaiclaw.compliance.effective.encrypt-at-rest", Boolean)
        !env.getProperty("jaiclaw.compliance.effective.require-https", Boolean)

        and: "and NOTHING outside the compliance namespace is touched"
        env.getProperty("jaiclaw.security.require-https") == null
        env.getProperty("jaiclaw.security.default-tool-profile") == null
        env.getProperty("jaiclaw.security.rate-limit.enabled") == null
    }

    def "profile=none is identical to absent"() {
        given:
        def env = new MockEnvironment().withProperty("jaiclaw.compliance.profile", "none")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        !env.getProperty("jaiclaw.compliance.effective.encrypt-at-rest", Boolean)
        env.getProperty("jaiclaw.security.default-tool-profile") == null
    }

    def "pre-existing profiles do not acquire the SOC 2 controls"() {
        given:
        def env = new MockEnvironment().withProperty("jaiclaw.compliance.profile", profile)

        when:
        pp.postProcessEnvironment(env, null)

        then: "adding soc2 must not silently harden gdpr/hipaa/federal deployments"
        !env.getProperty("jaiclaw.compliance.effective.audit-hash-chain", Boolean)
        !env.getProperty("jaiclaw.compliance.effective.encrypt-at-rest", Boolean)
        env.getProperty("jaiclaw.security.default-tool-profile") == null

        where:
        profile << ["gdpr", "hipaa", "both", "fedramp-moderate", "cmmc-l2", "fips"]
    }
}
