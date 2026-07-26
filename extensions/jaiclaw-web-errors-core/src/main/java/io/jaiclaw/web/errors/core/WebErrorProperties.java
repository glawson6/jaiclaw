package io.jaiclaw.web.errors.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Collections;
import java.util.Map;

/**
 * Bound configuration for the JaiClaw default web exception handlers.
 * The same record is consumed by both the WebMVC and WebFlux sibling
 * modules — behavior is identical across the two.
 *
 * <p>Prefix {@code jaiclaw.web.errors}. Every field has a documented
 * default; the record's compact constructor fills nulls with sane
 * values so binding partial YAML still yields a usable
 * {@code WebErrorProperties} instance.
 *
 * <p><b>Enable/disable</b> is not a field on this record. Autoconfig
 * activation is governed by
 * {@code @ConditionalOnProperty(prefix = "jaiclaw.web.errors",
 * name = "enabled", havingValue = "true", matchIfMissing = true)} on
 * the two web-stack autoconfig classes — the correct place for a
 * master-switch. Setting {@code jaiclaw.web.errors.enabled=false}
 * suppresses bean creation entirely, at which point this record is
 * never bound.
 *
 * @param bodyFormat               response-body shape. Default
 *                                 {@link WebErrorBodyFormat#OPAQUE}.
 * @param notFound                 404 mapping settings.
 * @param internalError            500 mapping settings.
 * @param includeExceptionMessage  when {@code true}, the response body
 *                                 for 500s contains {@code ex.getMessage()}
 *                                 in place of the fixed opaque body. Use
 *                                 in dev/local profiles only — default
 *                                 {@code false}.
 * @param contentType              content-type header for the response.
 *                                 Only used when
 *                                 {@code bodyFormat == PROBLEM_DETAIL}.
 *                                 Default {@code "application/problem+json"}.
 * @param statusOverrides          fully-qualified exception class name →
 *                                 status code overrides. Highest-priority
 *                                 mapping, applied before the framework
 *                                 category matchers.
 */
@ConfigurationProperties(prefix = "jaiclaw.web.errors")
public record WebErrorProperties(
        WebErrorBodyFormat bodyFormat,
        @NestedConfigurationProperty NotFound notFound,
        @NestedConfigurationProperty InternalError internalError,
        boolean includeExceptionMessage,
        String contentType,
        Map<String, Integer> statusOverrides) {

    public WebErrorProperties {
        if (bodyFormat == null) bodyFormat = WebErrorBodyFormat.OPAQUE;
        if (notFound == null) notFound = NotFound.DEFAULT;
        if (internalError == null) internalError = InternalError.DEFAULT;
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/problem+json";
        }
        if (statusOverrides == null) {
            statusOverrides = Map.of();
        } else {
            statusOverrides = Collections.unmodifiableMap(statusOverrides);
        }
    }

    /**
     * Convenience — all defaults. Equivalent to leaving
     * {@code jaiclaw.web.errors} entirely unset.
     */
    public static WebErrorProperties defaults() {
        return new WebErrorProperties(
                WebErrorBodyFormat.OPAQUE,
                NotFound.DEFAULT,
                InternalError.DEFAULT,
                false,
                "application/problem+json",
                Map.of());
    }

    /**
     * 404 mapping settings.
     *
     * @param status HTTP status to serve. Standard {@code 404}, but can
     *               be set to {@code 444} (nginx close) or {@code 204}
     *               for scanner-hostile setups.
     * @param body   response body string. Empty means literal empty body.
     */
    public record NotFound(int status, String body) {
        public static final NotFound DEFAULT = new NotFound(404, "Not Found");

        public NotFound {
            if (status < 100 || status > 599) status = 404;
            if (body == null) body = "";
        }
    }

    /**
     * 500 mapping settings.
     *
     * @param body body string served for unmapped exceptions.
     */
    public record InternalError(String body) {
        public static final InternalError DEFAULT = new InternalError("Internal Server Error");

        public InternalError {
            if (body == null) body = "";
        }
    }
}
