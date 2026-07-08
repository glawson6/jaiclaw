package io.jaiclaw.compliance

import org.springframework.mock.env.MockEnvironment
import spock.lang.Specification

class ComplianceEnvironmentPostProcessorSpec extends Specification {

    ComplianceEnvironmentPostProcessor pp = new ComplianceEnvironmentPostProcessor()

    def "profile=none produces all-effective-false"() {
        given:
        def env = new MockEnvironment()

        when:
        pp.postProcessEnvironment(env, null)

        then:
        env.getProperty("jaiclaw.compliance.effective.profile") == "none"
        env.getProperty("jaiclaw.compliance.effective.require-https", Boolean) == false
        env.getProperty("jaiclaw.compliance.effective.retention-enforcement", Boolean) == false
        env.getProperty("jaiclaw.compliance.effective.audit-chat-client", Boolean) == false
        env.getProperty("jaiclaw.compliance.effective.baa-warnings", Boolean) == false
        env.getProperty("jaiclaw.compliance.effective.prompt-redaction", Boolean) == false
        // security.require-https is NOT overwritten when profile is none
        env.getProperty("jaiclaw.security.require-https") == null
    }

    def "profile=hipaa flips effective flags on and propagates to security.require-https"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.profile", "hipaa")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        env.getProperty("jaiclaw.compliance.effective.profile") == "hipaa"
        env.getProperty("jaiclaw.compliance.effective.require-https", Boolean) == true
        env.getProperty("jaiclaw.compliance.effective.baa-warnings", Boolean) == true
        env.getProperty("jaiclaw.compliance.effective.prompt-redaction", Boolean) == true
        // Cross-subsystem propagation
        env.getProperty("jaiclaw.security.require-https", Boolean) == true
    }

    def "profile=gdpr turns on GDPR flags but not HIPAA-specific ones"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.profile", "gdpr")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        env.getProperty("jaiclaw.compliance.effective.require-https", Boolean) == true
        env.getProperty("jaiclaw.compliance.effective.retention-enforcement", Boolean) == true
        env.getProperty("jaiclaw.compliance.effective.audit-chat-client", Boolean) == true
        env.getProperty("jaiclaw.compliance.effective.baa-warnings", Boolean) == false
        env.getProperty("jaiclaw.compliance.effective.prompt-redaction", Boolean) == false
    }

    def "explicit flag override wins over profile default (turning off)"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.profile", "hipaa")
        env.setProperty("jaiclaw.compliance.require-https", "false")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        // HIPAA profile would default to true, but the explicit false wins
        env.getProperty("jaiclaw.compliance.effective.require-https", Boolean) == false
        // Other flags still follow the profile
        env.getProperty("jaiclaw.compliance.effective.baa-warnings", Boolean) == true
    }

    def "explicit flag override wins over profile default (turning on)"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.profile", "none")
        env.setProperty("jaiclaw.compliance.baa-warnings", "true")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        env.getProperty("jaiclaw.compliance.effective.baa-warnings", Boolean) == true
        // Other flags still follow the profile
        env.getProperty("jaiclaw.compliance.effective.require-https", Boolean) == false
    }

    def "pre-existing jaiclaw.security.require-https is preserved (operator opt-out)"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.profile", "hipaa")
        env.setProperty("jaiclaw.security.require-https", "false")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        // Post-processor does NOT overwrite an explicit downstream setting
        env.getProperty("jaiclaw.security.require-https", Boolean) == false
    }

    def "unknown profile string falls back to NONE with a warning (test the fallback path)"() {
        given:
        def env = new MockEnvironment()
        env.setProperty("jaiclaw.compliance.profile", "not-a-profile")

        when:
        pp.postProcessEnvironment(env, null)

        then:
        env.getProperty("jaiclaw.compliance.effective.profile") == "none"
        env.getProperty("jaiclaw.compliance.effective.require-https", Boolean) == false
    }
}
