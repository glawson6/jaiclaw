package io.jaiclaw.web.errors.webflux;

import io.jaiclaw.web.errors.core.WebErrorLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link WebErrorLogger} for the WebFlux autoconfig. Follows
 * the taptech-company Spring Boot logging policy at
 * {@code /Users/tap/dev/workspaces/taptech-company/docs/standards/spring-boot-logging.md}
 * lines 109-116:
 *
 * <ul>
 *   <li>5xx → {@code log.error(msg, args, throwable)} with the throwable
 *       as the last argument so SLF4J renders a full stack trace.</li>
 *   <li>4xx → {@code log.debug(msg, args, throwable)} so scanner probes
 *       don't spam production logs but can be traced via
 *       {@code /actuator/loggers} on demand.</li>
 * </ul>
 *
 * <p>Log messages carry the request summary + the throwable's simple
 * class name so grep-based ops workflows can filter without pulling
 * the full stack. No headers or bodies are logged — the policy warns
 * against unbounded log-line growth.
 */
public class Slf4jFluxWebErrorLogger implements WebErrorLogger {

    private static final Logger log = LoggerFactory.getLogger(Slf4jFluxWebErrorLogger.class);

    @Override
    public void log5xx(Throwable throwable, String requestSummary) {
        log.error("Unhandled exception on request {} - {}",
                safe(requestSummary),
                throwable == null ? "unknown" : throwable.getClass().getSimpleName(),
                throwable);
    }

    @Override
    public void log4xx(Throwable throwable, String requestSummary) {
        log.debug("Client error on request {} - {}",
                safe(requestSummary),
                throwable == null ? "unknown" : throwable.getClass().getSimpleName(),
                throwable);
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "<unknown>" : s;
    }
}
