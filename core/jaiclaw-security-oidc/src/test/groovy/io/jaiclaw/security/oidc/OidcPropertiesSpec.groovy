package io.jaiclaw.security.oidc

import spock.lang.Specification

class OidcPropertiesSpec extends Specification {

    def "applies defaults for absent values"() {
        when:
        def p = OidcProperties.defaults()

        then:
        p.tenantClaim() == OidcProperties.DEFAULT_TENANT_CLAIM
        p.jwsAlgorithms() == OidcProperties.DEFAULT_JWS_ALGORITHMS
        p.acceptedTypes() == OidcProperties.DEFAULT_ACCEPTED_TYPES
        p.requiredScope() == null
        p.scopeToProfile().isEmpty()
    }

    def "default algorithms are asymmetric only"() {
        expect: "a JWKS-validating resource server must never accept HMAC or none"
        OidcProperties.DEFAULT_JWS_ALGORITHMS.every { !it.startsWith("HS") && it != "none" }
    }

    def "accepts at+jwt by default"() {
        expect: "RFC 9068 access tokens use this typ; Spring's default verifier rejects it"
        OidcProperties.DEFAULT_ACCEPTED_TYPES.contains("at+jwt")
    }

    def "derives the JWKS uri from the issuer when not set"() {
        expect:
        new OidcProperties(issuer, null, null, null, null, null, null, null)
                .resolvedJwkSetUri() == expected

        where:
        issuer                            || expected
        "https://idp.example.com/oidc"    || "https://idp.example.com/oidc/jwks"
        "https://idp.example.com/oidc/"   || "https://idp.example.com/oidc/jwks"
        null                              || null
    }

    def "an explicit jwk-set-uri wins over the derived one"() {
        given: "providers differ — Keycloak uses /protocol/openid-connect/certs"
        def p = new OidcProperties("https://kc.example.com/realms/x", 
                "https://kc.example.com/realms/x/protocol/openid-connect/certs",
                null, null, null, null, null, null)

        expect:
        p.resolvedJwkSetUri().endsWith("/protocol/openid-connect/certs")
    }

    def "blank values normalise to null rather than empty strings"() {
        when:
        def p = new OidcProperties("  ", "  ", "  ", "  ", null, null, "  ", null)

        then:
        p.issuerUri() == null
        p.jwkSetUri() == null
        p.audience() == null
        p.requiredScope() == null
        p.tenantClaim() == OidcProperties.DEFAULT_TENANT_CLAIM
    }
}
