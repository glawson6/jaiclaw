package io.jaiclaw.security.oidc;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Rejects tokens not audienced to this deployment.
 *
 * <p>Without this check a token the same issuer minted for a <em>different</em>
 * service is accepted here — valid signature, valid issuer, wrong recipient.
 * That is a confused-deputy hole, and it is the gap the pre-1.3.0 HMAC
 * validator had: it never looked at {@code aud} at all.
 *
 * <p>Configuring no audience disables the check and logs nothing — the caller
 * ({@code JaiClawOidcAutoConfiguration}) warns at startup instead, so the
 * warning fires once rather than per request.
 *
 * <p>1.3.0.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token",
            "The required audience is missing from the token",
            "https://tools.ietf.org/html/rfc6750#section-3.1");

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (expectedAudience == null) {
            return OAuth2TokenValidatorResult.success();
        }
        List<String> audiences = token.getAudience();
        if (audiences != null && audiences.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        // Deliberately does not echo the expected audience — that would tell an
        // attacker holding some other valid token exactly what to request next.
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
