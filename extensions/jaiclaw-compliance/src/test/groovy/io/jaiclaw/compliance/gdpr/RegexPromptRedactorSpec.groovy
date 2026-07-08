package io.jaiclaw.compliance.gdpr

import io.jaiclaw.core.gdpr.PromptRedactor
import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContext
import spock.lang.Specification
import spock.lang.Subject

class RegexPromptRedactorSpec extends Specification {

    @Subject RegexPromptRedactor redactor = new RegexPromptRedactor()

    def "SSN is redacted when PHI processing is on"() {
        given:
        TenantContext ctx = phiContext(true)

        when:
        PromptRedactor.RedactionResult r = redactor.redact("My SSN is 123-45-6789 please", ctx)

        then:
        r.redactedContent() == "My SSN is [REDACTED-SSN] please"
        r.matches().size() == 1
        r.matches().first().pattern() == "ssn"
    }

    def "PHI-off tenant sees no redaction — content unchanged"() {
        given:
        TenantContext ctx = phiContext(false)

        when:
        PromptRedactor.RedactionResult r = redactor.redact("SSN 123-45-6789", ctx)

        then:
        r.redactedContent() == "SSN 123-45-6789"
        r.matches().isEmpty()
    }

    def "strict=true redacts regardless of tenant PHI flag"() {
        given:
        RegexPromptRedactor strict = new RegexPromptRedactor(true)
        TenantContext ctx = phiContext(false)

        when:
        PromptRedactor.RedactionResult r = strict.redact("SSN 123-45-6789", ctx)

        then:
        r.redactedContent() == "SSN [REDACTED-SSN]"
        r.matches().size() == 1
    }

    def "email + phone + DOB are all redacted"() {
        given:
        TenantContext ctx = phiContext(true)
        String input = "Contact bob@example.com or 555-123-4567, DOB 03/15/1985"

        when:
        PromptRedactor.RedactionResult r = redactor.redact(input, ctx)

        then:
        r.hasMatches()
        r.matches().collect { it.pattern() } as Set == ["email", "phone", "dob"] as Set
        !r.redactedContent().contains("bob@example.com")
        !r.redactedContent().contains("555-123-4567")
        !r.redactedContent().contains("03/15/1985")
    }

    def "empty / null content returns empty result cleanly"() {
        when:
        PromptRedactor.RedactionResult r1 = redactor.redact("", phiContext(true))
        PromptRedactor.RedactionResult r2 = redactor.redact(null, phiContext(true))

        then:
        r1.redactedContent() == ""
        r1.matches().isEmpty()
        r2.redactedContent() == ""
        r2.matches().isEmpty()
    }

    private TenantContext phiContext(boolean phi) {
        Map<String, Object> md = phi ? [(TenantContext.KEY_PHI_PROCESSING): true] : [:]
        return new DefaultTenantContext("acme", "acme", md)
    }
}
