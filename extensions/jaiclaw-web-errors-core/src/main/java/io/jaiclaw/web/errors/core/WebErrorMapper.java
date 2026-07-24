package io.jaiclaw.web.errors.core;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure decision function: given a {@link Throwable} and the current
 * {@link WebErrorProperties}, return the {@link WebErrorResponse} that
 * the JaiClaw default handlers should render.
 *
 * <p><b>No Spring web-stack dependency.</b> The mapper works entirely
 * off class-name strings that get compared against the throwable's
 * class + all its superclasses. This lets a single mapper serve both
 * the WebMVC and WebFlux autoconfig modules — each supplies the set of
 * class-name matchers for its stack via {@link ExceptionCategoryMatchers}.
 *
 * <p>Thread-safe; the mapper holds only immutable configuration.
 */
public class WebErrorMapper {

    /**
     * Class-name matchers for each framework exception category. The
     * WebMVC + WebFlux autoconfig modules supply their platform-specific
     * class-name sets; the mapper walks the throwable's inheritance
     * chain and compares against these sets.
     *
     * <p>Every field is a {@code Set<String>} of fully-qualified class
     * names. Empty sets are legal (means "this category is not handled
     * on this stack").
     *
     * @param notFoundClasses           404 candidates
     * @param methodNotAllowedClasses   405 candidates
     * @param unsupportedMediaTypeClasses 415 candidates
     * @param notAcceptableClasses      406 candidates
     * @param validationClasses         400 candidates
     * @param unauthenticatedClasses    401 candidates
     * @param forbiddenClasses          403 candidates
     * @param responseStatusClasses     dynamic-status candidates
     *                                  ({@code ResponseStatusException})
     */
    public record ExceptionCategoryMatchers(
            Set<String> notFoundClasses,
            Set<String> methodNotAllowedClasses,
            Set<String> unsupportedMediaTypeClasses,
            Set<String> notAcceptableClasses,
            Set<String> validationClasses,
            Set<String> unauthenticatedClasses,
            Set<String> forbiddenClasses,
            Set<String> responseStatusClasses) {

        public ExceptionCategoryMatchers {
            notFoundClasses = safeCopy(notFoundClasses);
            methodNotAllowedClasses = safeCopy(methodNotAllowedClasses);
            unsupportedMediaTypeClasses = safeCopy(unsupportedMediaTypeClasses);
            notAcceptableClasses = safeCopy(notAcceptableClasses);
            validationClasses = safeCopy(validationClasses);
            unauthenticatedClasses = safeCopy(unauthenticatedClasses);
            forbiddenClasses = safeCopy(forbiddenClasses);
            responseStatusClasses = safeCopy(responseStatusClasses);
        }

        private static Set<String> safeCopy(Set<String> s) {
            return s == null ? Set.of() : Set.copyOf(s);
        }
    }

    private final ExceptionCategoryMatchers matchers;

    public WebErrorMapper(ExceptionCategoryMatchers matchers) {
        this.matchers = matchers == null
                ? new ExceptionCategoryMatchers(Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of())
                : matchers;
    }

    /**
     * Compute the response for a throwable + current properties. Never
     * throws — a mapper failure is itself an unhandled exception, and
     * the caller must always be able to render <i>something</i>.
     *
     * <p>Decision order (first match wins):
     * <ol>
     *   <li>Status overrides from properties (fully-qualified class-name key).</li>
     *   <li>Specific framework categories — 404 / 405 / 415 / 406 / 401 / 403 /
     *       validation. These take precedence over the generic
     *       {@code ResponseStatusException} branch below because the reactive
     *       stack's {@code NoResourceFoundException} extends
     *       {@code ResponseStatusException} and carries the internal
     *       "No static resource /.env for request '/.env'." message in its
     *       reason — matching notFoundClasses first ensures the opaque body
     *       wins.</li>
     *   <li>{@code ResponseStatusException} — use its own
     *       {@code getStatusCode()} + {@code getReason()}.</li>
     *   <li>Everything else → 500 opaque.</li>
     * </ol>
     */
    public WebErrorResponse map(Throwable throwable, WebErrorProperties properties) {
        if (throwable == null) {
            return unhandled(new IllegalStateException("null throwable"), properties);
        }
        WebErrorProperties props = properties != null ? properties : WebErrorProperties.defaults();

        // 1. Static status-override from properties.
        Set<String> chain = classChain(throwable);
        Integer overridden = firstOverride(chain, props.statusOverrides());
        if (overridden != null) {
            return build(overridden, opaqueBodyFor(overridden, props), throwable,
                    FrameworkExceptionKind.OVERRIDDEN, props);
        }

        // 2. Specific framework-category matches — checked BEFORE the
        //    generic ResponseStatusException fallback so subclasses like
        //    reactive NoResourceFoundException render an opaque body
        //    rather than their internal reason string.
        if (anyMatch(chain, matchers.notFoundClasses())) {
            return build(props.notFound().status(), props.notFound().body(),
                    throwable, FrameworkExceptionKind.NOT_FOUND, props);
        }
        if (anyMatch(chain, matchers.methodNotAllowedClasses())) {
            return build(405, opaqueBodyFor(405, props), throwable,
                    FrameworkExceptionKind.METHOD_NOT_ALLOWED, props);
        }
        if (anyMatch(chain, matchers.unsupportedMediaTypeClasses())) {
            return build(415, opaqueBodyFor(415, props), throwable,
                    FrameworkExceptionKind.UNSUPPORTED_MEDIA_TYPE, props);
        }
        if (anyMatch(chain, matchers.notAcceptableClasses())) {
            return build(406, opaqueBodyFor(406, props), throwable,
                    FrameworkExceptionKind.NOT_ACCEPTABLE, props);
        }
        if (anyMatch(chain, matchers.unauthenticatedClasses())) {
            return build(401, opaqueBodyFor(401, props), throwable,
                    FrameworkExceptionKind.UNAUTHENTICATED, props);
        }
        if (anyMatch(chain, matchers.forbiddenClasses())) {
            return build(403, opaqueBodyFor(403, props), throwable,
                    FrameworkExceptionKind.FORBIDDEN, props);
        }
        if (anyMatch(chain, matchers.validationClasses())) {
            return build(400, opaqueBodyFor(400, props), throwable,
                    FrameworkExceptionKind.VALIDATION, props);
        }

        // 3. ResponseStatusException — read its own status + reason.
        if (anyMatch(chain, matchers.responseStatusClasses())) {
            ResponseStatusInfo info = extractResponseStatus(throwable);
            String body = info.reason() != null && !info.reason().isBlank()
                    ? info.reason()
                    : opaqueBodyFor(info.status(), props);
            return build(info.status(), body, throwable,
                    FrameworkExceptionKind.RESPONSE_STATUS, props);
        }

        // 4. Unhandled → 500.
        return unhandled(throwable, props);
    }

    // ── helpers ───────────────────────────────────────────

    private WebErrorResponse unhandled(Throwable throwable, WebErrorProperties props) {
        String body = props.includeExceptionMessage() && throwable.getMessage() != null
                ? throwable.getMessage()
                : props.internalError().body();
        return build(500, body, throwable, FrameworkExceptionKind.UNHANDLED, props);
    }

    private WebErrorResponse build(int status, String body, Throwable throwable,
                                    FrameworkExceptionKind kind,
                                    WebErrorProperties props) {
        String contentType = props.bodyFormat() == WebErrorBodyFormat.PROBLEM_DETAIL
                ? props.contentType()
                : "text/plain;charset=UTF-8";
        String rendered = props.bodyFormat() == WebErrorBodyFormat.PROBLEM_DETAIL
                ? renderProblemDetail(status, body)
                : body;
        return new WebErrorResponse(status, rendered, contentType, kind, throwable);
    }

    /**
     * RFC 7807 {@code application/problem+json} rendering. Deliberately
     * hand-rolled to avoid a Jackson dep on the shared module —
     * mapping is small and the values are known-safe strings from
     * properties, not user input.
     */
    private String renderProblemDetail(int status, String detail) {
        String safeDetail = escapeJsonString(detail == null ? "" : detail);
        return "{"
                + "\"type\":\"about:blank\","
                + "\"title\":\"" + escapeJsonString(reasonFor(status)) + "\","
                + "\"status\":" + status + ","
                + "\"detail\":\"" + safeDetail + "\""
                + "}";
    }

    private static String escapeJsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Opaque body served for a specific status. 404 uses the configured
     * {@code notFound.body}; 500 uses {@code internalError.body} (or
     * {@code ex.getMessage()} when {@code includeExceptionMessage=true}).
     * All others fall back to a status-derived opaque string.
     */
    private String opaqueBodyFor(int status, WebErrorProperties props) {
        if (status == props.notFound().status()) return props.notFound().body();
        if (status >= 500 && status < 600) return props.internalError().body();
        return reasonFor(status);
    }

    private static String reasonFor(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 406 -> "Not Acceptable";
            case 409 -> "Conflict";
            case 415 -> "Unsupported Media Type";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> status + " Error";
        };
    }

    /**
     * Walk the throwable's class and every superclass, returning the
     * set of fully-qualified class names. Used for category matching.
     */
    private static Set<String> classChain(Throwable throwable) {
        Set<String> names = new HashSet<>();
        Class<?> c = throwable.getClass();
        while (c != null && c != Object.class) {
            names.add(c.getName());
            c = c.getSuperclass();
        }
        return names;
    }

    private static boolean anyMatch(Set<String> chain, Set<String> candidates) {
        if (candidates.isEmpty()) return false;
        for (String name : candidates) {
            if (chain.contains(name)) return true;
        }
        return false;
    }

    private static Integer firstOverride(Set<String> chain, java.util.Map<String, Integer> overrides) {
        for (String name : chain) {
            Integer v = overrides.get(name);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * Reflectively extracts {@code getStatusCode().value()} +
     * {@code getReason()} from a Spring
     * {@code ResponseStatusException} without importing the class.
     * Falls back to 500 + null reason on any reflection failure.
     */
    private static ResponseStatusInfo extractResponseStatus(Throwable throwable) {
        try {
            java.lang.reflect.Method statusCodeMethod = throwable.getClass().getMethod("getStatusCode");
            Object statusCode = statusCodeMethod.invoke(throwable);
            int status = 500;
            if (statusCode != null) {
                java.lang.reflect.Method valueMethod = statusCode.getClass().getMethod("value");
                Object value = valueMethod.invoke(statusCode);
                if (value instanceof Integer i) status = i;
            }
            String reason = null;
            try {
                java.lang.reflect.Method getReason = throwable.getClass().getMethod("getReason");
                Object reasonObj = getReason.invoke(throwable);
                if (reasonObj instanceof String s) reason = s;
            } catch (NoSuchMethodException ignored) {
                // Reason absent — not all subclasses expose it.
            }
            return new ResponseStatusInfo(status, reason);
        } catch (Exception e) {
            return new ResponseStatusInfo(500, null);
        }
    }

    private record ResponseStatusInfo(int status, String reason) {}
}
