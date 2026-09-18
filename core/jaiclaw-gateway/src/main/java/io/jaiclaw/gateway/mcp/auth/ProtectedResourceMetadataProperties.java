package io.jaiclaw.gateway.mcp.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.List;

/**
 * Configuration for the RFC 9728 Protected Resource Metadata document
 * ({@code jaiclaw.mcp.auth.*}).
 *
 * <p>This is how an MCP client discovers <em>which</em> authorization server to
 * authenticate against. The client fetches the document from the
 * <strong>resource server</strong> — i.e. from JaiClaw — not from the identity
 * provider, and identity providers generally do not publish it on a resource's
 * behalf. So JaiClaw has to serve it itself.
 *
 * <p>Provider-agnostic: point {@code authorization-servers} at any issuer.
 *
 * @param enabled              whether to publish the document. Default false —
 *                             an unauthenticated discovery endpoint should be a
 *                             deliberate choice.
 * @param resource             this resource server's identifier, matching the
 *                             {@code aud} its tokens carry. Defaults to
 *                             {@code jaiclaw.security.oidc.audience} when unset.
 * @param authorizationServers issuers that may mint tokens for this resource.
 *                             Defaults to {@code jaiclaw.security.oidc.issuer-uri}.
 * @param scopesSupported      scopes advertised to clients. Advisory — the
 *                             authoritative check is token validation.
 * @param documentationUri     optional human-readable docs link.
 *
 * <p>1.3.0.
 */
@ConfigurationProperties(prefix = "jaiclaw.mcp.auth")
public record ProtectedResourceMetadataProperties(
        boolean enabled,
        String resource,
        List<String> authorizationServers,
        List<String> scopesSupported,
        String documentationUri
) {
    @ConstructorBinding
    public ProtectedResourceMetadataProperties {
        authorizationServers = authorizationServers == null
                ? List.of() : List.copyOf(authorizationServers);
        scopesSupported = scopesSupported == null ? List.of() : List.copyOf(scopesSupported);
        if (resource != null && resource.isBlank()) resource = null;
        if (documentationUri != null && documentationUri.isBlank()) documentationUri = null;
    }

    /**
     * Programmatic defaults.
     *
     * <p>A {@code public static} factory rather than a no-arg constructor —
     * Spring Boot 4's record binder picks a public constructor by parameter
     * count and can silently choose an overload over the canonical one, which
     * drops nested YAML values.
     */
    public static ProtectedResourceMetadataProperties defaults() {
        return new ProtectedResourceMetadataProperties(false, null, null, null, null);
    }
}
