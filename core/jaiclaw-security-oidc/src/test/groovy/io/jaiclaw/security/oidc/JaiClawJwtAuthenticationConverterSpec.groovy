package io.jaiclaw.security.oidc

import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.security.authn.JaiClawAuthentication
import org.springframework.security.oauth2.jwt.Jwt
import spock.lang.Specification

import java.time.Instant

class JaiClawJwtAuthenticationConverterSpec extends Specification {

    def props = new OidcProperties(
            "https://idp.example.com/oidc", null, "https://jaiclaw.example.com/api",
            "organization_id", null, null, null,
            ["jaiclaw:tools:full": "FULL", "jaiclaw:tools:coding": "CODING",
             "jaiclaw:tools:minimal": "MINIMAL"])

    def mapper = new ScopeToolProfileMapper(props.scopeToProfile())
    def converter = new JaiClawJwtAuthenticationConverter(props, mapper)

    def "produces a JaiClawAuthentication carrying tenant, profile and scopes"() {
        given:
        def jwt = jwt([sub: "user-123", organization_id: "acme",
                       scope: "openid jaiclaw:tools:coding"])

        when:
        JaiClawAuthentication auth = converter.convert(jwt)

        then: "the same principal type every other mode produces — this is the seam"
        auth.source() == JaiClawAuthentication.AuthSource.OIDC
        auth.principal == "user-123"
        auth.tenantContext().get().getTenantId() == "acme"
        auth.toolProfile().get() == ToolProfile.CODING
        auth.authorities*.authority.containsAll(["SCOPE_openid", "SCOPE_jaiclaw:tools:coding"])
    }

    def "a token with no tenant claim still authenticates — TenantGuard decides if that matters"() {
        given: "a stock provider token with no organization claim"
        def jwt = jwt([sub: "user-123", scope: "openid"])

        when:
        JaiClawAuthentication auth = converter.convert(jwt)

        then: "the legacy HMAC validator rejected this outright; single-tenant needs it to work"
        auth != null
        auth.tenantContext().isEmpty()
    }

    def "highest-privilege scope wins"() {
        given:
        def jwt = jwt([sub: "u", scope: "jaiclaw:tools:minimal jaiclaw:tools:full"])

        expect:
        converter.convert(jwt).toolProfile().get() == ToolProfile.FULL
    }

    def "unmapped scopes defer to the configured default"() {
        given:
        def jwt = jwt([sub: "u", scope: "openid profile"])

        expect: "empty, not a guess — the caller falls back to default-tool-profile"
        converter.convert(jwt).toolProfile().isEmpty()
    }

    def "reads scopes from a space-delimited string, a list, or the scp claim"() {
        expect:
        JaiClawJwtAuthenticationConverter.extractScopes(jwt([scope: "a b c"])) == ["a", "b", "c"]
        JaiClawJwtAuthenticationConverter.extractScopes(jwt([scope: ["a", "b"]])) == ["a", "b"]
        JaiClawJwtAuthenticationConverter.extractScopes(jwt([scp: ["x", "y"]])) == ["x", "y"]
        JaiClawJwtAuthenticationConverter.extractScopes(jwt([scp: "p q"])) == ["p", "q"]
        JaiClawJwtAuthenticationConverter.extractScopes(jwt([sub: "u"])) == []
    }

    def "a token with no subject does not blow up"() {
        expect:
        converter.convert(jwt([organization_id: "acme"])).principal == "unknown"
    }

    def "honours a custom tenant claim name"() {
        given:
        def custom = new OidcProperties("https://idp.example.com/oidc", null, null,
                "tid", null, null, null, [:])
        def jwt = jwt([sub: "u", tid: "globex"])

        expect:
        new JaiClawJwtAuthenticationConverter(custom, null)
                .convert(jwt).tenantContext().get().getTenantId() == "globex"
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .claims { it.putAll(claims) }
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build()
    }
}
