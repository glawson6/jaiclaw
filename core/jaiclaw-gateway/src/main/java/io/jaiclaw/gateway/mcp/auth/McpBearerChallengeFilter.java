package io.jaiclaw.gateway.mcp.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;

/**
 * Adds {@code resource_metadata} to the {@code WWW-Authenticate} header on 401s
 * from MCP endpoints.
 *
 * <p>This is the other half of RFC 9728 discovery. Publishing the metadata
 * document is not enough on its own — a client that gets a bare
 * {@code WWW-Authenticate: Bearer} has no way to know <em>where</em> the
 * document lives. The challenge points at it:
 *
 * <pre>
 * WWW-Authenticate: Bearer resource_metadata="https://jaiclaw.example.com/.well-known/oauth-protected-resource"
 * </pre>
 *
 * <p>The header is only augmented, never replaced: whatever the authentication
 * layer already said about {@code error} and {@code error_description} is
 * preserved, with the metadata pointer appended.
 *
 * <p>1.3.0.
 */
public class McpBearerChallengeFilter extends OncePerRequestFilter {

    private static final String HEADER = "WWW-Authenticate";

    private final String metadataUrl;
    private final List<PathPattern> protectedPatterns;

    public McpBearerChallengeFilter(String metadataUrl, List<String> protectedPaths) {
        this.metadataUrl = metadataUrl;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.protectedPatterns = protectedPaths.stream().map(parser::parse).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return protectedPatterns.stream().noneMatch(p -> p.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);

        if (response.getStatus() != HttpServletResponse.SC_UNAUTHORIZED) {
            return;
        }
        // Committed responses cannot take further headers; nothing useful can be
        // done about it here, and failing the request would be worse than a
        // client falling back to its configured issuer.
        if (response.isCommitted()) {
            return;
        }

        String existing = response.getHeader(HEADER);
        String pointer = "resource_metadata=\"" + metadataUrl + "\"";

        if (existing == null || existing.isBlank()) {
            response.setHeader(HEADER, "Bearer " + pointer);
        } else if (!existing.contains("resource_metadata=")) {
            response.setHeader(HEADER, existing + ", " + pointer);
        }
    }
}
