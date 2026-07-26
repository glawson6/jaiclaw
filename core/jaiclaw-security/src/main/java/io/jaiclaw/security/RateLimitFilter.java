package io.jaiclaw.security;

import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.PathContainer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-sender rate limiting filter for {@code /api/**} endpoints.
 * Uses an in-memory {@link ConcurrentHashMap} with atomic counters — no external dependencies.
 * <p>
 * Sender is identified as the JWT subject (if authenticated) or client IP (fallback).
 * Returns HTTP 429 with {@code Retry-After} and {@code X-RateLimit-*} headers when exceeded.
 * A background virtual thread cleans expired entries periodically.
 * <p>
 * The primary gate is the {@code /api/**} whitelist — only requests whose path
 * starts with {@code /api/} are candidates for rate-limiting. On top of that,
 * {@code jaiclaw.security.rate-limit.skip-paths} adds Ant-pattern exclusions
 * (e.g., {@code /api/health} to keep liveness probes off the rate limit).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int maxRequests;
    private final int windowSeconds;
    private final ConcurrentHashMap<String, SenderWindow> windows = new ConcurrentHashMap<>();
    private final List<PathPattern> skipPatterns;

    public RateLimitFilter(int maxRequests, int windowSeconds, int cleanupIntervalSeconds) {
        this(maxRequests, windowSeconds, cleanupIntervalSeconds, null);
    }

    /**
     * @param skipPaths additional exclusions layered on top of the built-in
     *                  {@code /api/**} whitelist gate. Each entry is a Spring
     *                  path pattern ({@link PathPattern} shape).
     *                  {@code null} or empty means no additional exclusions —
     *                  only the whitelist gate applies.
     */
    public RateLimitFilter(int maxRequests, int windowSeconds, int cleanupIntervalSeconds,
                            List<String> skipPaths) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        List<String> effective = (skipPaths == null) ? List.of() : skipPaths;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.skipPatterns = effective.stream()
                .map(parser::parse)
                .toList();

        Thread.ofVirtual().name("rate-limit-cleanup").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(cleanupIntervalSeconds * 1000L);
                    long now = System.currentTimeMillis();
                    long windowMs = windowSeconds * 1000L;
                    windows.entrySet().removeIf(e -> now - e.getValue().windowStart > windowMs * 2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return true;   // primary whitelist gate — unchanged
        PathContainer pathContainer = PathContainer.parsePath(path);
        for (PathPattern pattern : skipPatterns) {
            if (pattern.matches(pathContainer)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String sender = resolveSender(request);
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        SenderWindow window = windows.compute(sender, (key, existing) -> {
            if (existing == null || now - existing.windowStart > windowMs) {
                return new SenderWindow(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        int currentCount = window.count.get();

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, maxRequests - currentCount)));

        if (currentCount > maxRequests) {
            long retryAfter = Math.max(1, (windowMs - (now - window.windowStart)) / 1000);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setStatus(429);
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"retry_after\":" + retryAfter + "}");
            log.debug("Rate limit exceeded for sender={}, count={}", sender, currentCount);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveSender(HttpServletRequest request) {
        // Include tenantId in rate limit key when tenant context is set
        String tenantPrefix = "";
        TenantContext tenantCtx = TenantContextHolder.get();
        if (tenantCtx != null) {
            tenantPrefix = tenantCtx.getTenantId() + ":";
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String subject) {
            return tenantPrefix + "jwt:" + subject;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return tenantPrefix + "ip:" + forwarded.split(",")[0].trim();
        }
        return tenantPrefix + "ip:" + request.getRemoteAddr();
    }

    private static class SenderWindow {
        final long windowStart;
        final AtomicInteger count;

        SenderWindow(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
