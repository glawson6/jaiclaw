package io.jaiclaw.security.oidc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;
import java.util.Map;

/**
 * Configuration for OIDC / OAuth 2.0 resource-server authentication
 * ({@code jaiclaw.security.oidc.*}), active when
 * {@code jaiclaw.security.mode=oidc}.
 *
 * <p><strong>Provider-agnostic.</strong> Nothing here names a vendor. The
 * defaults are chosen to work with the widest range of providers, and every
 * value that differs between them — signing algorithm, tenant claim, audience —
 * is configuration rather than code.
 *
 * <p>Sample configurations for several providers are in
 * {@code docs/user/OIDC-AUTHENTICATION.md}.
 *
 * @param issuerUri      the provider's issuer, e.g. {@code https://idp.example.com/oidc}.
 *                       Required. Used both to validate the {@code iss} claim and,
 *                       when {@code jwkSetUri} is absent, to derive the JWKS location.
 * @param jwkSetUri      explicit JWKS endpoint. Optional — defaults to
 *                       {@code {issuerUri}/jwks}, which is not universal, so providers
 *                       that publish elsewhere must set it.
 * @param audience       this deployment's resource indicator, matched against {@code aud}.
 *                       Strongly recommended: without it, a token minted for a
 *                       <em>different</em> service by the same issuer is accepted here.
 * @param tenantClaim    claim carrying the tenant id. Defaults to {@code organization_id}.
 * @param jwsAlgorithms  accepted signing algorithms. Defaults to {@code [RS256, ES256, ES384]}.
 *                       Acts as an allowlist — a token signed with anything else is
 *                       rejected regardless of what its header claims.
 * @param acceptedTypes  accepted JOSE {@code typ} header values. Defaults to
 *                       {@code [at+jwt, JWT]}; RFC 9068 access tokens use {@code at+jwt},
 *                       which Spring's default verifier rejects outright.
 * @param requiredScope  optional scope every caller must hold. Blank disables the check.
 * @param scopeToProfile maps a granted scope to a {@link io.jaiclaw.core.tool.ToolProfile}.
 *                       When several match, the highest privilege wins.
 *
 * <p>1.3.0.
 */
@ConfigurationProperties(prefix = "jaiclaw.security.oidc")
public record OidcProperties(
        String issuerUri,
        String jwkSetUri,
        String audience,
        String tenantClaim,
        List<String> jwsAlgorithms,
        List<String> acceptedTypes,
        String requiredScope,
        Map<String, String> scopeToProfile
) {
    /** Claim carrying the tenant id when none is configured. */
    public static final String DEFAULT_TENANT_CLAIM = "organization_id";

    /**
     * Signing algorithms accepted by default.
     *
     * <p>Covers the common cases: RSA (`RS256`, the most widespread) and EC
     * (`ES256`/`ES384`, which some providers — Logto among them — use for their
     * default keypair). Deliberately excludes `none` and every HMAC variant: a
     * resource server validating against a public JWKS must never accept a
     * symmetric algorithm, or an attacker who learns the public key can sign
     * tokens with it.
     */
    public static final List<String> DEFAULT_JWS_ALGORITHMS =
            List.of("RS256", "ES256", "ES384");

    /** JOSE {@code typ} values accepted by default. */
    public static final List<String> DEFAULT_ACCEPTED_TYPES = List.of("at+jwt", "JWT");

    @ConstructorBinding
    public OidcProperties {
        if (tenantClaim == null || tenantClaim.isBlank()) {
            tenantClaim = DEFAULT_TENANT_CLAIM;
        }
        jwsAlgorithms = (jwsAlgorithms == null || jwsAlgorithms.isEmpty())
                ? DEFAULT_JWS_ALGORITHMS : List.copyOf(jwsAlgorithms);
        acceptedTypes = (acceptedTypes == null || acceptedTypes.isEmpty())
                ? DEFAULT_ACCEPTED_TYPES : List.copyOf(acceptedTypes);
        if (requiredScope != null && requiredScope.isBlank()) requiredScope = null;
        if (issuerUri != null && issuerUri.isBlank()) issuerUri = null;
        if (jwkSetUri != null && jwkSetUri.isBlank()) jwkSetUri = null;
        if (audience != null && audience.isBlank()) audience = null;
        scopeToProfile = scopeToProfile == null ? Map.of() : Map.copyOf(scopeToProfile);
    }

    /**
     * Programmatic defaults for tests and builders.
     *
     * <p>A {@code public static} factory rather than a no-arg constructor: Spring
     * Boot 4's record binder picks a public constructor by parameter count and can
     * silently choose an overload over the {@code @ConstructorBinding} canonical
     * one, which drops nested YAML values. One public constructor per
     * {@code @ConfigurationProperties} record is the safe shape.
     */
    public static OidcProperties defaults() {
        return new OidcProperties(null, null, null, null, null, null, null, null);
    }

    /**
     * The JWKS endpoint, derived from the issuer when not set explicitly.
     *
     * <p>{@code {issuer}/jwks} is a convention, not a standard — providers differ
     * (Keycloak uses {@code /protocol/openid-connect/certs}). Set
     * {@code jwk-set-uri} explicitly when the derived value is wrong.
     */
    public String resolvedJwkSetUri() {
        if (jwkSetUri != null) return jwkSetUri;
        if (issuerUri == null) return null;
        return issuerUri.endsWith("/") ? issuerUri + "jwks" : issuerUri + "/jwks";
    }
}
