package io.jaiclaw.gateway.mcp.auth;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Serves the RFC 9728 Protected Resource Metadata document at
 * {@code /.well-known/oauth-protected-resource}.
 *
 * <h2>Why JaiClaw publishes this itself</h2>
 * An MCP client that receives a 401 needs to discover which authorization
 * server to go to. RFC 9728 defines that discovery as a fetch against the
 * <strong>resource server</strong> — so the document has to come from JaiClaw.
 * Identity providers do not publish it on a resource's behalf (Logto, for one,
 * does not implement RFC 9728 at all), which makes this endpoint a hard
 * requirement rather than a convenience.
 *
 * <p>The document is public and unauthenticated by necessity: a client cannot
 * authenticate until it has read it. It discloses only issuer URLs and scope
 * names — information a client must have to authenticate at all — and never
 * anything about who is registered or what they may access. Publication is
 * nevertheless opt-in via {@code jaiclaw.mcp.auth.enabled}.
 *
 * <p>1.3.0.
 */
@RestController
public class ProtectedResourceMetadataController {

    private final ProtectedResourceMetadataProperties properties;
    private final String resolvedResource;
    private final List<String> resolvedAuthorizationServers;

    public ProtectedResourceMetadataController(
            ProtectedResourceMetadataProperties properties,
            String resolvedResource,
            List<String> resolvedAuthorizationServers) {
        this.properties = properties;
        this.resolvedResource = resolvedResource;
        this.resolvedAuthorizationServers = List.copyOf(resolvedAuthorizationServers);
    }

    @GetMapping(path = "/.well-known/oauth-protected-resource",
            produces = "application/json")
    public ResponseEntity<Map<String, Object>> metadata() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resource", resolvedResource);
        body.put("authorization_servers", resolvedAuthorizationServers);
        if (!properties.scopesSupported().isEmpty()) {
            body.put("scopes_supported", properties.scopesSupported());
        }
        // Only the Authorization header is supported. RFC 6750 also allows the
        // token in a form body or query string; both are refused — a query
        // parameter lands in access logs, proxy logs, and Referer headers.
        body.put("bearer_methods_supported", List.of("header"));
        if (properties.documentationUri() != null) {
            body.put("resource_documentation", properties.documentationUri());
        }

        return ResponseEntity.ok()
                // Stable enough to cache; short enough that rotating an issuer
                // propagates within the hour without a client restart.
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(body);
    }
}
