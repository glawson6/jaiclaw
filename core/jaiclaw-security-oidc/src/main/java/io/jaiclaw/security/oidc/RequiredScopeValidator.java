package io.jaiclaw.security.oidc;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires every caller to hold one named scope.
 *
 * <p>A coarse deployment-wide gate, useful when an issuer serves several
 * unrelated applications and JaiClaw should only accept tokens explicitly
 * granted for it. Finer-grained authorization belongs in
 * {@code scope-to-profile} or method security, not here.
 *
 * <p>Disabled when {@code jaiclaw.security.oidc.required-scope} is unset.
 *
 * <p>1.3.0.
 */
public class RequiredScopeValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INSUFFICIENT_SCOPE = new OAuth2Error(
            "insufficient_scope",
            "The token does not carry the scope required by this deployment",
            "https://tools.ietf.org/html/rfc6750#section-3.1");

    private final String requiredScope;

    public RequiredScopeValidator(String requiredScope) {
        this.requiredScope = requiredScope;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (requiredScope == null) {
            return OAuth2TokenValidatorResult.success();
        }
        return JaiClawJwtAuthenticationConverter.extractScopes(token).contains(requiredScope)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INSUFFICIENT_SCOPE);
    }
}
