package io.jaiclaw.web.errors.mvc;

import io.jaiclaw.web.errors.core.WebErrorLogger;
import io.jaiclaw.web.errors.core.WebErrorMapper;
import io.jaiclaw.web.errors.core.WebErrorProperties;
import io.jaiclaw.web.errors.core.WebErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lowest-precedence {@code @RestControllerAdvice} that catches every
 * throwable Spring's own handler chain missed and routes it through
 * {@link WebErrorMapper} to produce a status + body. Adopter
 * {@code @RestControllerAdvice}s continue to win because this one runs
 * <b>after</b> them (per {@link Ordered#LOWEST_PRECEDENCE} minus one).
 *
 * <p>The class is intentionally non-final so app-level handlers can
 * {@code extends JaiclawDefaultExceptionHandler} and add their own
 * {@code @ExceptionHandler(BusinessException.class)} methods while
 * inheriting all framework-exception mapping for free.
 *
 * <p><b>Why one catch-all instead of one method per category?</b>
 * Method-per-category means adopters who subclass have to know which
 * ones to override. Routing through the mapper keeps the adopter's
 * override surface small (add methods for business types; parent
 * handles the rest) while retaining the ability to swap the mapper
 * bean if downstream needs completely different mapping logic.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class JaiclawDefaultExceptionHandler {

    private final WebErrorMapper mapper;
    private final WebErrorProperties properties;
    private final WebErrorLogger errorLogger;

    public JaiclawDefaultExceptionHandler(WebErrorMapper mapper,
                                          WebErrorProperties properties,
                                          WebErrorLogger errorLogger) {
        this.mapper = mapper;
        this.properties = properties;
        this.errorLogger = errorLogger;
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<String> handleAny(Throwable throwable, HttpServletRequest request) {
        WebErrorResponse response = mapper.map(throwable, properties);
        String requestSummary = describe(request);

        if (response.isServerError()) {
            errorLogger.log5xx(throwable, requestSummary);
        } else if (response.isClientError()) {
            errorLogger.log4xx(throwable, requestSummary);
        }

        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(response.contentType());
        } catch (RuntimeException e) {
            contentType = MediaType.TEXT_PLAIN;
        }

        return ResponseEntity
                .status(response.status())
                .contentType(contentType)
                .body(response.body());
    }

    private static String describe(HttpServletRequest request) {
        if (request == null) return "<unknown>";
        String method = safe(request.getMethod());
        String uri = safe(request.getRequestURI());
        return method + " " + uri;
    }

    private static String safe(String s) {
        return s == null ? "?" : s;
    }
}
