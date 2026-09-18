package io.jaiclaw.gateway.mcp.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Publishes RFC 9728 Protected Resource Metadata so MCP clients can discover
 * which authorization server protects this deployment.
 *
 * <p>Registered by {@code JaiClawGatewayAutoConfiguration} via {@code @Import},
 * matching how the rest of the gateway's beans are wired.
 *
 * <p>Opt-in via {@code jaiclaw.mcp.auth.enabled=true}. Provider-agnostic — the
 * advertised issuer is whatever is configured, and defaults are derived from
 * {@code jaiclaw.security.oidc.*} so a correctly configured OIDC deployment
 * usually needs only the one flag.
 *
 * <p>1.3.0.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
@ConditionalOnProperty(prefix = "jaiclaw.mcp.auth", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ProtectedResourceMetadataProperties.class)
public class McpAuthMetadataAutoConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(McpAuthMetadataAutoConfiguration.class);

    static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

    /** MCP surfaces whose 401s should carry the discovery pointer. */
    static final List<String> PROTECTED_PATHS = List.of("/mcp/**");

    @Bean
    @ConditionalOnMissingBean(ProtectedResourceMetadataController.class)
    public ProtectedResourceMetadataController protectedResourceMetadataController(
            ProtectedResourceMetadataProperties properties, Environment environment) {

        // Default to the OIDC settings: the resource identifier IS the audience
        // its tokens carry, and the authorization server IS the issuer that
        // mints them. Restating both would only create a way to disagree.
        String resource = properties.resource() != null
                ? properties.resource()
                : environment.getProperty("jaiclaw.security.oidc.audience");

        List<String> issuers = !properties.authorizationServers().isEmpty()
                ? properties.authorizationServers()
                : issuerFromOidc(environment);

        if (resource == null) {
            throw new IllegalStateException(
                    "jaiclaw.mcp.auth.enabled=true but no resource identifier is available. "
                            + "Set jaiclaw.mcp.auth.resource, or configure "
                            + "jaiclaw.security.oidc.audience.");
        }
        if (issuers.isEmpty()) {
            throw new IllegalStateException(
                    "jaiclaw.mcp.auth.enabled=true but no authorization server is available. "
                            + "Set jaiclaw.mcp.auth.authorization-servers, or configure "
                            + "jaiclaw.security.oidc.issuer-uri.");
        }

        log.info("MCP protected-resource metadata published at {}: resource={}, "
                + "authorization_servers={}", METADATA_PATH, resource, issuers);
        return new ProtectedResourceMetadataController(properties, resource, issuers);
    }

    /**
     * Points MCP 401s at the metadata document.
     *
     * <p>The URL must be absolute, and the deployment's public address is not
     * reliably knowable from inside the JVM (proxies, ingress, port mapping).
     * {@code jaiclaw.mcp.auth.public-base-url} is therefore consulted first,
     * falling back to the resource identifier's origin — which is usually the
     * same host, since the resource identifier is normally a public URL.
     */
    @Bean
    @ConditionalOnMissingBean(McpBearerChallengeFilter.class)
    public McpBearerChallengeFilter mcpBearerChallengeFilter(
            ProtectedResourceMetadataProperties properties, Environment environment) {

        String baseUrl = environment.getProperty("jaiclaw.mcp.auth.public-base-url");
        if (baseUrl == null || baseUrl.isBlank()) {
            String resource = properties.resource() != null
                    ? properties.resource()
                    : environment.getProperty("jaiclaw.security.oidc.audience");
            baseUrl = originOf(resource);
        }
        if (baseUrl == null) {
            log.warn("Could not determine a public base URL for the MCP metadata pointer. "
                    + "Set jaiclaw.mcp.auth.public-base-url — without it, 401 responses carry "
                    + "no resource_metadata hint and clients must be pre-configured.");
            baseUrl = "";
        }
        String metadataUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + METADATA_PATH
                : baseUrl + METADATA_PATH;

        return new McpBearerChallengeFilter(metadataUrl, PROTECTED_PATHS);
    }

    /**
     * Registers the challenge filter with the servlet container directly.
     *
     * <p>Deliberately <em>not</em> added to a Spring Security chain: that would
     * require {@code jaiclaw-security-oidc} to depend on {@code jaiclaw-gateway},
     * inverting the module hierarchy (the gateway sits above security, not
     * below). A plain servlet filter wraps the response just as effectively for
     * a header set after the chain completes, and keeps the dependency graph
     * acyclic.
     *
     * <p>Ordered after Spring Security so the 401 it augments has already been
     * produced.
     */
    @Bean
    @ConditionalOnMissingBean(name = "mcpBearerChallengeFilterRegistration")
    public org.springframework.boot.web.servlet.FilterRegistrationBean<McpBearerChallengeFilter>
            mcpBearerChallengeFilterRegistration(McpBearerChallengeFilter filter) {
        var registration =
                new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/mcp/*");
        registration.setOrder(org.springframework.core.Ordered.LOWEST_PRECEDENCE - 100);
        registration.setName("mcpBearerChallengeFilter");
        return registration;
    }

    private static List<String> issuerFromOidc(Environment environment) {
        String issuer = environment.getProperty("jaiclaw.security.oidc.issuer-uri");
        return (issuer == null || issuer.isBlank()) ? List.of() : List.of(issuer);
    }

    /** {@code https://host/path} → {@code https://host}. */
    static String originOf(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            StringBuilder origin = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
            if (uri.getPort() != -1) origin.append(':').append(uri.getPort());
            return origin.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
