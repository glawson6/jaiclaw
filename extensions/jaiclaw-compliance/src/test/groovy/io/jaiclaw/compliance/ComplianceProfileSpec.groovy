package io.jaiclaw.compliance

import spock.lang.Specification

class ComplianceProfileSpec extends Specification {

    def "NONE profile has every feature default-off"() {
        given:
        def p = ComplianceProfile.NONE

        expect:
        !p.requiresHttps()
        !p.requiresRetentionEnforcement()
        !p.requiresAuditChatClient()
        !p.requiresBaaWarnings()
        !p.requiresPromptRedaction()
        !p.requiresFipsEnforced()
        !p.requiresFedrampWarnings()
        !p.requiresCuiWarnings()
    }

    def "GDPR profile turns on GDPR-relevant features"() {
        given:
        def p = ComplianceProfile.GDPR

        expect:
        p.requiresHttps()
        p.requiresRetentionEnforcement()
        p.requiresAuditChatClient()
        !p.requiresBaaWarnings()          // BAA is HIPAA-specific
        !p.requiresPromptRedaction()      // Redaction is HIPAA-specific
    }

    def "HIPAA profile turns on HIPAA-relevant features"() {
        given:
        def p = ComplianceProfile.HIPAA

        expect:
        p.requiresHttps()
        p.requiresRetentionEnforcement()
        p.requiresAuditChatClient()
        p.requiresBaaWarnings()
        p.requiresPromptRedaction()
    }

    def "BOTH profile turns on the union"() {
        given:
        def p = ComplianceProfile.BOTH

        expect:
        p.requiresHttps()
        p.requiresRetentionEnforcement()
        p.requiresAuditChatClient()
        p.requiresBaaWarnings()
        p.requiresPromptRedaction()
    }

    def "FEDRAMP_MODERATE profile turns on federal-baseline features"() {
        given:
        def p = ComplianceProfile.FEDRAMP_MODERATE

        expect:
        p.requiresHttps()
        p.requiresRetentionEnforcement()
        p.requiresAuditChatClient()
        p.requiresFipsEnforced()
        p.requiresFedrampWarnings()
        !p.requiresBaaWarnings()          // BAA is HIPAA-specific
        !p.requiresPromptRedaction()      // Redaction is HIPAA/CMMC-specific
        !p.requiresCuiWarnings()          // CUI is CMMC-specific
    }

    def "CMMC_L2 profile turns on CUI-baseline features"() {
        given:
        def p = ComplianceProfile.CMMC_L2

        expect:
        p.requiresHttps()
        p.requiresRetentionEnforcement()
        p.requiresAuditChatClient()
        p.requiresPromptRedaction()
        p.requiresCuiWarnings()
        !p.requiresBaaWarnings()          // BAA is HIPAA-specific
        !p.requiresFipsEnforced()         // FIPS opt-in orthogonally
        !p.requiresFedrampWarnings()      // FedRAMP is a different boundary
    }

    def "FIPS profile turns on only the FIPS posture check"() {
        given:
        def p = ComplianceProfile.FIPS

        expect:
        p.requiresFipsEnforced()
        !p.requiresHttps()
        !p.requiresRetentionEnforcement()
        !p.requiresAuditChatClient()
        !p.requiresBaaWarnings()
        !p.requiresPromptRedaction()
        !p.requiresFedrampWarnings()
        !p.requiresCuiWarnings()
    }
}
