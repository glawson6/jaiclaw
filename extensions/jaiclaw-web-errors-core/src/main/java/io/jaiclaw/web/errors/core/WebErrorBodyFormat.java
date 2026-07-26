package io.jaiclaw.web.errors.core;

/**
 * Response-body shape selector for the JaiClaw default web exception
 * handlers.
 *
 * <p>{@link #OPAQUE} (the default) renders a fixed string body with no
 * framework-specific structure — scanners can't fingerprint the app as
 * Spring, can't tell if the response came from JaiClaw's handler or an
 * upstream proxy. Matches the "opaque body" pattern the Sentinel operator
 * uses at the ingress layer.
 *
 * <p>{@link #PROBLEM_DETAIL} renders an RFC 7807-shaped
 * {@code application/problem+json} body. Useful when downstream API
 * clients expect structured error payloads and the deployment isn't
 * public-facing.
 */
public enum WebErrorBodyFormat {
    /** Fixed-string body, no framework tell. */
    OPAQUE,
    /** RFC 7807 {@code application/problem+json} body. */
    PROBLEM_DETAIL
}
