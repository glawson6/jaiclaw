package io.jaiclaw.web.errors.core

import spock.lang.Specification
import spock.lang.Unroll

class WebErrorMapperSpec extends Specification {

    // Test-only stand-in classes with the exact names the mvc/webflux
    // modules will match against. The mapper is class-name-string driven
    // so this works without a real Spring web dep.

    static class FakeNoResourceFound extends RuntimeException {
        FakeNoResourceFound(String msg) { super(msg) }
    }
    static class FakeMethodNotSupported extends RuntimeException {}
    static class FakeUnsupportedMediaType extends RuntimeException {}
    static class FakeNotAcceptable extends RuntimeException {}
    static class FakeValidation extends RuntimeException {
        FakeValidation(String msg) { super(msg) }
    }
    static class FakeAuthentication extends RuntimeException {}
    static class FakeAccessDenied extends RuntimeException {}
    static class BusinessConflict extends RuntimeException {}

    /**
     * A fake ResponseStatusException. Reflectively probed by the mapper
     * — must expose getStatusCode() returning something with a value()
     * method AND getReason(). Mimics Spring's shape.
     */
    static class FakeResponseStatus extends RuntimeException {
        private final FakeStatusCode statusCode
        private final String reason
        FakeResponseStatus(int status, String reason) {
            super("Status " + status + ": " + reason)
            this.statusCode = new FakeStatusCode(status)
            this.reason = reason
        }
        FakeStatusCode getStatusCode() { statusCode }
        String getReason() { reason }
    }

    static class FakeStatusCode {
        private final int value
        FakeStatusCode(int value) { this.value = value }
        int value() { value }
    }

    // ── fixtures ───────────────────────────────────────

    WebErrorMapper.ExceptionCategoryMatchers matchers = new WebErrorMapper.ExceptionCategoryMatchers(
            Set.of(FakeNoResourceFound.name),
            Set.of(FakeMethodNotSupported.name),
            Set.of(FakeUnsupportedMediaType.name),
            Set.of(FakeNotAcceptable.name),
            Set.of(FakeValidation.name),
            Set.of(FakeAuthentication.name),
            Set.of(FakeAccessDenied.name),
            Set.of(FakeResponseStatus.name))

    WebErrorMapper mapper = new WebErrorMapper(matchers)
    WebErrorProperties defaults = WebErrorProperties.defaults()

    // ── happy paths ───────────────────────────────────

    @Unroll
    def "#throwable → status #expectedStatus, kind #expectedKind"() {
        when:
        WebErrorResponse resp = mapper.map(throwable, defaults)

        then:
        resp.status() == expectedStatus
        resp.kind() == expectedKind
        resp.cause() == throwable

        where:
        throwable                                       || expectedStatus || expectedKind
        new FakeNoResourceFound(".env")                 || 404            || FrameworkExceptionKind.NOT_FOUND
        new FakeMethodNotSupported()                    || 405            || FrameworkExceptionKind.METHOD_NOT_ALLOWED
        new FakeUnsupportedMediaType()                  || 415            || FrameworkExceptionKind.UNSUPPORTED_MEDIA_TYPE
        new FakeNotAcceptable()                         || 406            || FrameworkExceptionKind.NOT_ACCEPTABLE
        new FakeValidation("bad field")                 || 400            || FrameworkExceptionKind.VALIDATION
        new FakeAuthentication()                        || 401            || FrameworkExceptionKind.UNAUTHENTICATED
        new FakeAccessDenied()                          || 403            || FrameworkExceptionKind.FORBIDDEN
        new IllegalStateException("kaboom")             || 500            || FrameworkExceptionKind.UNHANDLED
    }

    def "ResponseStatusException uses its own status + reason"() {
        given:
        FakeResponseStatus ex = new FakeResponseStatus(418, "I'm a teapot")

        when:
        WebErrorResponse resp = mapper.map(ex, defaults)

        then:
        resp.status() == 418
        resp.kind() == FrameworkExceptionKind.RESPONSE_STATUS
        resp.body() == "I'm a teapot"
    }

    def "ResponseStatusException with blank reason falls back to opaque body"() {
        given:
        FakeResponseStatus ex = new FakeResponseStatus(503, "")

        when:
        WebErrorResponse resp = mapper.map(ex, defaults)

        then:
        resp.status() == 503
        resp.body() == "Internal Server Error"  // 5xx opaque fallback
    }

    // ── static overrides ──────────────────────────────

    def "status-overrides property wins over framework matchers"() {
        given:
        WebErrorProperties props = new WebErrorProperties(
                WebErrorBodyFormat.OPAQUE,
                WebErrorProperties.NotFound.DEFAULT,
                WebErrorProperties.InternalError.DEFAULT,
                false,
                "application/problem+json",
                Map.of(BusinessConflict.name, 409))

        when:
        WebErrorResponse resp = mapper.map(new BusinessConflict(), props)

        then:
        resp.status() == 409
        resp.kind() == FrameworkExceptionKind.OVERRIDDEN
    }

    // ── opaque vs problem-detail body ─────────────────

    def "OPAQUE format renders plain-text opaque body for 500"() {
        when:
        WebErrorResponse resp = mapper.map(new IllegalStateException("secret"), defaults)

        then:
        resp.body() == "Internal Server Error"
        resp.contentType().startsWith("text/plain")
        !resp.body().contains("secret")
    }

    def "PROBLEM_DETAIL format renders application/problem+json body"() {
        given:
        WebErrorProperties props = new WebErrorProperties(
                WebErrorBodyFormat.PROBLEM_DETAIL,
                WebErrorProperties.NotFound.DEFAULT,
                WebErrorProperties.InternalError.DEFAULT,
                false,
                "application/problem+json",
                Map.of())

        when:
        WebErrorResponse resp = mapper.map(new IllegalStateException("secret"), props)

        then:
        resp.contentType() == "application/problem+json"
        resp.body().contains("\"status\":500")
        resp.body().contains("\"title\":\"Internal Server Error\"")
        // detail = internalError.body (still opaque; the flag only controls format shape)
        !resp.body().contains("secret")
    }

    def "includeExceptionMessage=true leaks the exception message in the body"() {
        given:
        WebErrorProperties props = new WebErrorProperties(
                WebErrorBodyFormat.OPAQUE,
                WebErrorProperties.NotFound.DEFAULT,
                WebErrorProperties.InternalError.DEFAULT,
                true,
                "application/problem+json",
                Map.of())

        when:
        WebErrorResponse resp = mapper.map(new IllegalStateException("this-is-the-secret"), props)

        then:
        resp.body() == "this-is-the-secret"
    }

    // ── boundary cases ────────────────────────────────

    def "null throwable does not crash the mapper"() {
        expect:
        mapper.map(null, defaults).status() == 500
    }

    def "null properties fall back to defaults"() {
        when:
        WebErrorResponse resp = mapper.map(new IllegalStateException("x"), null)

        then:
        resp.status() == 500
        resp.body() == "Internal Server Error"
    }

    def "notFound.status override applies to 404 responses"() {
        given:
        WebErrorProperties props = new WebErrorProperties(
                WebErrorBodyFormat.OPAQUE,
                new WebErrorProperties.NotFound(444, ""),
                WebErrorProperties.InternalError.DEFAULT,
                false, "application/problem+json", Map.of())

        when:
        WebErrorResponse resp = mapper.map(new FakeNoResourceFound(".env"), props)

        then:
        resp.status() == 444
        resp.body() == ""
    }

    def "helper flags isServerError / isClientError"() {
        expect:
        mapper.map(new IllegalStateException(), defaults).isServerError()
        !mapper.map(new IllegalStateException(), defaults).isClientError()
        mapper.map(new FakeNoResourceFound(".env"), defaults).isClientError()
        !mapper.map(new FakeNoResourceFound(".env"), defaults).isServerError()
    }

    // ── JSON escaping ─────────────────────────────────

    def "problem-detail body escapes quote characters in the detail field"() {
        given:
        WebErrorProperties props = new WebErrorProperties(
                WebErrorBodyFormat.PROBLEM_DETAIL,
                new WebErrorProperties.NotFound(404, 'No static resource ".env"'),
                WebErrorProperties.InternalError.DEFAULT,
                false, "application/problem+json", Map.of())

        when:
        WebErrorResponse resp = mapper.map(new FakeNoResourceFound(".env"), props)

        then:
        resp.body().contains('\\"\\.env\\"'.replace("\\.", "."))  // quotes escaped
        // Body is still valid single-line JSON
        !resp.body().contains("\n")
    }
}
