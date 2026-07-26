package io.jaiclaw.web.errors.core;

/**
 * Enforcement point for the "every 5xx must produce an ERROR log line
 * with a full stack trace" contract from the taptech-company logging
 * policy at
 * {@code /Users/tap/dev/workspaces/taptech-company/docs/standards/spring-boot-logging.md}
 * lines 114-116.
 *
 * <p>Both the WebMVC and WebFlux default handlers call
 * {@link #log5xx(Throwable, String)} <b>before</b> rendering the
 * response body, so no unlogged 5xx can leave the app. 4xx paths route
 * through {@link #log4xx(Throwable, String)} which defaults to DEBUG
 * — developers can bump the level via {@code /actuator/loggers}
 * without a redeploy when investigating a spike.
 *
 * <p>Adopters override this bean when they want structured logging
 * (JSON layout, correlation-id stamping, external appenders); the
 * default SLF4J-vanilla impls are supplied in each web-stack module.
 */
public interface WebErrorLogger {

    /**
     * Log a 5xx-mapped throwable at ERROR with the full stack trace.
     *
     * @param throwable      the exception that caused the response;
     *                       never null.
     * @param requestSummary a short {@code "{method} {path}"} string.
     *                       No headers, no bodies — the logging policy
     *                       warns against unbounded log-line growth.
     */
    void log5xx(Throwable throwable, String requestSummary);

    /**
     * Log a 4xx-mapped throwable at DEBUG with the throwable attached.
     * The DEBUG level keeps normal client errors (bad JSON, missing
     * params, 404s from scanners) out of production logs by default;
     * operators bump the level when they need to trace a specific
     * regression.
     */
    void log4xx(Throwable throwable, String requestSummary);
}
