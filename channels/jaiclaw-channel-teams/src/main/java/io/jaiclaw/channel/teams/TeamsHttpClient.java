package io.jaiclaw.channel.teams;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Abstraction over HTTP transport for Microsoft Teams / Bot Framework calls.
 *
 * <p>Introduced at 1.0.0 alongside the RestTemplate → RestClient migration
 * (Boot 4 / Framework 7): {@link TeamsAdapter}, {@link TeamsTokenManager},
 * and {@link TeamsJwtValidator} depend on this interface so unit specs can
 * mock at this layer instead of at the underlying HTTP client's fluent
 * surface. Reference impl: {@link RestClientTeamsHttpClient}.
 */
public interface TeamsHttpClient {

    /**
     * POST a JSON body with an optional Bearer auth header. Returns the
     * parsed JSON response body.
     *
     * @param url         fully-qualified URL
     * @param bearerToken bearer token, or null to skip Authorization
     * @param body        JSON body (typically a Map)
     */
    JsonNode postJson(String url, String bearerToken, Map<String, Object> body);

    /**
     * POST an application/x-www-form-urlencoded body, returning the response
     * body as a String (used for OAuth token endpoints).
     */
    String postForm(String url, String formBody);

    /**
     * GET a text/JSON resource, returning the response body as a String.
     * Used for OpenID metadata + JWKS fetches.
     */
    String getString(String url);

    /**
     * GET raw bytes (used for Teams file downloads with a Bearer auth).
     */
    byte[] getBytes(String url, String bearerToken);
}
