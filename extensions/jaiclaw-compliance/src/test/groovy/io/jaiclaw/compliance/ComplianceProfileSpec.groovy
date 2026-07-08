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
}
