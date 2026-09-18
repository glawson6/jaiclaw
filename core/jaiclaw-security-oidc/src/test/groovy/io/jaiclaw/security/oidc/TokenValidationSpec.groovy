package io.jaiclaw.security.oidc

import org.springframework.security.oauth2.jwt.Jwt
import spock.lang.Specification

import java.time.Instant

/**
 * The validators that stand between a signed token and a request. Each of these
 * checks was absent from the pre-1.3.0 HMAC validator.
 */
class TokenValidationSpec extends Specification {

    def "audience validator accepts a token audienced to this deployment"() {
        given:
        def validator = new AudienceValidator("https://jaiclaw.example.com/api")

        expect:
        !validator.validate(jwt(["https://jaiclaw.example.com/api"])).hasErrors()
    }

    def "audience validator rejects a token minted for another service"() {
        given: "a perfectly valid token from the same issuer, for a different audience"
        def validator = new AudienceValidator("https://jaiclaw.example.com/api")

        when:
        def result = validator.validate(jwt(["https://billing.example.com/api"]))

        then: "without this check it would be accepted — a confused-deputy hole"
        result.hasErrors()
        result.errors.first().errorCode == "invalid_token"
    }

    def "audience validator rejects a token with no audience at all"() {
        expect:
        new AudienceValidator("https://jaiclaw.example.com/api").validate(jwt(null)).hasErrors()
    }

    def "audience validator is inert when no audience is configured"() {
        expect: "opt-out is allowed, but the auto-config warns loudly at startup"
        !new AudienceValidator(null).validate(jwt(["anything"])).hasErrors()
    }

    def "audience error does not disclose the expected audience"() {
        when:
        def result = new AudienceValidator("https://secret-internal.example.com/api")
                .validate(jwt(["other"]))

        then: "telling the caller what to ask for next would be an own goal"
        !result.errors.first().description.contains("secret-internal")
    }

    def "required-scope validator gates on a named scope"() {
        given:
        def validator = new RequiredScopeValidator("jaiclaw:access")

        expect:
        !validator.validate(jwtWithScope("openid jaiclaw:access")).hasErrors()
        validator.validate(jwtWithScope("openid profile")).hasErrors()
    }

    def "required-scope validator is inert when unset"() {
        expect:
        !new RequiredScopeValidator(null).validate(jwtWithScope("openid")).hasErrors()
    }

    private static Jwt jwt(List<String> audience) {
        def b = Jwt.withTokenValue("t").header("alg", "RS256").subject("u")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
        if (audience != null) b = b.audience(audience)
        b.build()
    }

    private static Jwt jwtWithScope(String scope) {
        Jwt.withTokenValue("t").header("alg", "RS256").subject("u")
                .claim("scope", scope)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .build()
    }
}
