package io.jaiclaw.tools.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates session tokens from completed security handshakes on {@code /mcp/**} paths.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Skips {@code /mcp/security/**} (handshake endpoints use bootstrap auth, not session tokens)</li>
 *   <li>For other {@code /mcp/**} paths: checks {@code Authorization: Bearer <token>}</li>
 *   <li>On valid: sets {@link SecurityContextHolder} with the authenticated subject</li>
 *   <li>On invalid or missing: returns 401</li>
 *   <li>Non-MCP paths pass through unchanged</li>
 * </ul>
 *
 * <p>Spring Security compliance:
 * <ul>
 *   <li>Extends {@code OncePerRequestFilter} (same as JwtAuthenticationFilter)</li>
 *   <li>Sets {@code UsernamePasswordAuthenticationToken} in SecurityContextHolder</li>
 *   <li>Clears context in finally block</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HandshakeTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HandshakeTokenFilter.class);

    private final HandshakeSessionStore sessionStore;
    private final String handshakeServerPath;

    /**
     * @param sessionStore        the session store to validate tokens against
     * @param handshakeServerName the MCP server name for handshake endpoints (e.g. "security")
     */
    public HandshakeTokenFilter(HandshakeSessionStore sessionStore, String handshakeServerName) {
        this.sessionStore = sessionStore;
        this.handshakeServerPath = "/mcp/" + handshakeServerName;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip handshake endpoints (they use bootstrap auth)
        if (path.startsWith(handshakeServerPath + "/") || path.equals(handshakeServerPath)) {
            return true;
        }
        // Only filter /mcp/** paths
        return !path.startsWith("/mcp/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // If already authenticated (e.g. by JwtAuthenticationFilter), pass through
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No Bearer token on protected MCP path: {}", request.getRequestURI());
            sendUnauthorized(response, "Missing session token. Complete security handshake first.");
            return;
        }

        String token = authHeader.substring(7).trim();

        var maybeSession = sessionStore.findByToken(token);
        if (maybeSession.isEmpty()) {
            log.warn("Invalid handshake session token on {}", request.getRequestURI());
            sendUnauthorized(response, "Invalid or expired session token. Complete a new security handshake.");
            return;
        }

        HandshakeSession session = maybeSession.get();

        // Set Spring Security context
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_HANDSHAKE"));
        var authentication = new UsernamePasswordAuthenticationToken(
                session.getVerifiedSubject(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Handshake token validated for '{}' on {}", session.getVerifiedSubject(), request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"unauthorized\", \"message\": \"%s\"}".formatted(message));
    }
}
