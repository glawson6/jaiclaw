package io.jclaw.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-sender rate limiting filter for {@code /api/**} endpoints.
 * Uses an in-memory {@link ConcurrentHashMap} with atomic counters — no external dependencies.
 * <p>
 * Sender is identified as the JWT subject (if authenticated) or client IP (fallback).
 * Returns HTTP 429 with {@code Retry-After} and {@code X-RateLimit-*} headers when exceeded.
 * A background virtual thread cleans expired entries periodically.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final int maxRequests;
    private final int windowSeconds;
    private final ConcurrentHashMap<String, SenderWindow> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(int maxRequests, int windowSeconds, int cleanupIntervalSeconds) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;

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
        return !path.startsWith("/api/");
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String subject) {
            return "jwt:" + subject;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip:" + forwarded.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
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
