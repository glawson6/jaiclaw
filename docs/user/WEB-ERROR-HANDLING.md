# Web Error Handling

JaiClaw ships default exception handlers for both Spring web stacks —
`jaiclaw-web-errors-mvc` for servlet apps and `jaiclaw-web-errors-webflux`
for reactive apps. Both are pulled in transitively by
`jaiclaw-spring-boot-starter` and activate on classpath detection.

## What they fix

Every JaiClaw-consuming Spring Boot app has (historically) written its
own `@RestControllerAdvice` with a final `@ExceptionHandler(Throwable.class)`
returning `HTTP 500` with `ex.getMessage()` in the body. That pattern
has two bugs:

1. **It turns genuine 404s into 500s.** Spring 6+ throws
   `NoResourceFoundException` for unknown static-resource paths. A
   catch-all on `Throwable` intercepts it before Spring's default
   404-mapping runs. `GET /.env` returns
   `500 "404 NOT_FOUND \"No static resource .env.\""`.

2. **It leaks `ex.getMessage()` to unauthenticated callers AND
   doesn't log the exception at all.** Attackers learn "this is a
   Spring app with static-resource resolution enabled"; operators
   can't debug what actually broke because no ERROR log was ever
   written.

The two `jaiclaw-web-errors-*` modules install lowest-precedence
handlers that (a) route framework exceptions to their real HTTP
status, (b) serve an opaque body by default so scanners can't
fingerprint the app, and (c) log every 5xx at ERROR with the full
throwable via a `WebErrorLogger` SPI.

## Precedence contract

Both handlers are deliberately positioned so **adopter advice always
wins**:

- **WebMVC** — `JaiclawDefaultExceptionHandler` is a
  `@RestControllerAdvice` annotated with
  `@Order(Ordered.LOWEST_PRECEDENCE - 1)`. Any adopter
  `@RestControllerAdvice` without an explicit `@Order` sits at
  `Ordered.LOWEST_PRECEDENCE` and matches earlier in the resolution
  chain. Adopters who want to override JaiClaw's mapping simply add
  their own `@ExceptionHandler(NoResourceFoundException.class)` (or
  whichever type) in their advice and it takes precedence.

- **WebFlux** — `JaiclawWebFluxErrorHandler` implements
  `ErrorWebExceptionHandler` at `Ordered.HIGHEST_PRECEDENCE + 100`.
  Higher precedence than Spring Boot's
  `DefaultErrorWebExceptionHandler` (which sits at
  `Ordered.LOWEST_PRECEDENCE - 1`, a very large positive int).
  Adopters who need to run in front register their own
  `WebExceptionHandler` bean at `Ordered.HIGHEST_PRECEDENCE + 50`
  (or any lower number) and control the request path.

## Auto-enable / opt-out

Pulled in transitively when the consuming app depends on
`jaiclaw-spring-boot-starter`:

```xml
<dependency>
    <groupId>io.jaiclaw</groupId>
    <artifactId>jaiclaw-spring-boot-starter</artifactId>
</dependency>
```

Both modules ship — only the one whose web stack is present
activates, gated on `@ConditionalOnClass`:

- `jaiclaw-web-errors-mvc` needs
  `org.springframework.web.servlet.HandlerExceptionResolver`.
- `jaiclaw-web-errors-webflux` needs
  `org.springframework.web.server.WebExceptionHandler`.

Turn both off with a single property:

```yaml
jaiclaw:
  web:
    errors:
      enabled: false        # default true
```

## Properties reference

All under prefix `jaiclaw.web.errors`.

```yaml
jaiclaw:
  web:
    errors:
      enabled: true                          # master off-switch
      body-format: opaque                    # opaque | problem-detail
      not-found:
        status: 404                          # 404 | 444 (nginx close) | 204
        body: "Not Found"                    # empty string = zero-byte body
      internal-error:
        body: "Internal Server Error"
      include-exception-message: false       # dev-only; puts ex.getMessage() in 500 bodies
      content-type: "application/problem+json"  # used only when body-format=problem-detail
      status-overrides:
        # Fully-qualified class name → HTTP status. Any throwable whose
        # class or any superclass matches gets remapped to this status
        # with an opaque body. Runs BEFORE any framework-category match,
        # so this is the escape hatch for "map my business exception to
        # 409 without writing a handler."
        # "com.example.MyBusinessException": 409
```

### Body formats

- **`opaque`** (default) — a fixed bytes-for-bytes body. For 404 this
  is `notFound.body`; for 500 it's `internalError.body`; for other
  framework statuses it's the standard reason phrase (`"Method Not
  Allowed"`, `"Unsupported Media Type"`, etc.). Content-type is
  always `text/plain;charset=UTF-8`. Nothing about the underlying
  framework or its exception message leaks.

- **`problem-detail`** — [RFC 7807] JSON with `type`, `title`,
  `status`, `detail` fields. `detail` carries the same opaque string
  that `opaque` mode would return (so no exception message leaks
  unless `include-exception-message: true`). Content-type from
  `contentType` property, default `application/problem+json`.

### Setting `include-exception-message: true`

**Only for local development or a locked-down internal-only staging
environment.** When true, 500-tier responses put `ex.getMessage()` in
the body. This is the exact leak the module was built to close —
never enable in prod.

## Extending the handler (WebMVC)

`JaiclawDefaultExceptionHandler` is intentionally non-final. Subclass
it and add business-exception handlers; framework mapping is inherited:

```java
@RestControllerAdvice
public class MyBusinessExceptionAdvice extends JaiclawDefaultExceptionHandler {

    public MyBusinessExceptionAdvice(WebErrorMapper mapper,
                                     WebErrorProperties props,
                                     WebErrorLogger logger) {
        super(mapper, props, logger);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> orderNotFound(OrderNotFoundException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(NOT_FOUND, ex.getMessage());
        body.setType(URI.create("https://errors.example.com/order-not-found"));
        return ResponseEntity.status(NOT_FOUND).body(body);
    }
}
```

Spring picks up the subclass because of `@RestControllerAdvice` on it.
The parent's `@Order(LOWEST_PRECEDENCE - 1)` is inherited, so the
inherited `@ExceptionHandler(Throwable.class)` runs after adopter
advice as intended.

To customise the mapper itself (e.g., add a new class-name to the
validation set), provide a `@Bean WebErrorMapper` in your
`@Configuration` — `@ConditionalOnMissingBean` on the default means
your bean wins.

## Extending the handler (WebFlux)

Reactive apps override by providing a `WebExceptionHandler` bean at a
higher-priority `@Order`:

```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE + 50)   // in front of JaiClaw's
public WebExceptionHandler myBusinessHandler(WebErrorMapper mapper) {
    return new MyBusinessWebExceptionHandler(mapper);
}
```

Because `WebExceptionHandler` beans are collected into an ordered
chain, the first bean to return a non-empty `Mono` wins. JaiClaw's
handler sits behind yours.

## Logging contract

Every 5xx goes through the module's `WebErrorLogger` — the enforcement
point the [Spring Boot logging policy] calls out (line 114-116).
Contract:

```java
public interface WebErrorLogger {
    void log5xx(Throwable ex, String requestSummary);
    void log4xx(Throwable ex, String requestSummary);   // DEBUG by default
}
```

- 5xx path: `log.error("Unhandled exception on request {} - {}",
  requestSummary, ex.getClass().getSimpleName(), ex);` — the
  throwable is the **last** argument so SLF4J renders a full stack
  trace, per the policy at line 109.
- 4xx path: `log.debug(...)` — same format, throwable included so
  developers can bump the level via `/actuator/loggers` to
  investigate scanner-probe spikes without a redeploy.
- `requestSummary` = `"{method} {path}"` — no headers, no body.
  The logging policy warns against unbounded log-line growth.

Both modules ship a default `WebErrorLogger` bean at
`@ConditionalOnMissingBean` so adopters can override for structured
logging (e.g., a JSON layout appender that stamps request-id):

```java
@Bean
public WebErrorLogger structuredLogger() {
    return new StructuredJsonWebErrorLogger(clock, requestIdProvider);
}
```

## Migration guide

If your app already has a hand-rolled `@RestControllerAdvice` with a
catch-all on `Throwable`, migration is a single edit — narrow the
catch-all to your business exception base type:

**Before:**

```java
@RestControllerAdvice
public class MyAppAdvice {
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<String> catchAll(Throwable ex) {
        return ResponseEntity.status(500).body(ex.getMessage());
    }
}
```

**After:**

```java
@RestControllerAdvice
public class MyAppAdvice extends JaiclawDefaultExceptionHandler {
    public MyAppAdvice(WebErrorMapper m, WebErrorProperties p, WebErrorLogger l) {
        super(m, p, l);
    }
    // Add per-business-exception handlers as needed. Framework mapping
    // is inherited from the parent.
}
```

Framework exceptions (`NoResourceFoundException`,
`HttpRequestMethodNotSupportedException`, `MethodArgumentNotValidException`,
etc.) route through the parent's mapper. Business exceptions you
handle explicitly stay under your control.

## Verification

Send a scanner-style request to a JaiClaw-consuming app running with
the starter:

```bash
curl -sv http://localhost:8080/.env
```

**Before this module:**

```
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{"type":"...","title":"Internal Server Error","status":500,
 "detail":"404 NOT_FOUND \"No static resource .env.\""}
```

**With this module (default `opaque` body):**

```
HTTP/1.1 404 Not Found
Content-Type: text/plain;charset=UTF-8

Not Found
```

Server-side log line:

```
DEBUG i.j.w.e.m.Slf4jMvcWebErrorLogger : Client error on request GET /.env - NoResourceFoundException
```

(The 404 path is a 4xx and logs at DEBUG. Test a genuine 500 by
throwing a runtime exception from a controller — expect the same
line at ERROR with a full stack trace attached.)

## Related

- [`taptech-company/docs/standards/spring-boot-logging.md`] —
  logging policy this module enforces at line 114-116.

[RFC 7807]: https://datatracker.ietf.org/doc/html/rfc7807
[Spring Boot logging policy]: /Users/tap/dev/workspaces/taptech-company/docs/standards/spring-boot-logging.md
[`taptech-company/docs/standards/spring-boot-logging.md`]: /Users/tap/dev/workspaces/taptech-company/docs/standards/spring-boot-logging.md
