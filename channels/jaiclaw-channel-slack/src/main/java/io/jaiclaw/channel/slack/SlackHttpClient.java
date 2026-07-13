package io.jaiclaw.channel.slack;

import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;

import java.util.Map;

/**
 * Abstraction over HTTP transport for Slack Web API calls.
 *
 * <p>Introduced at 1.0.0 alongside the RestTemplate → RestClient migration
 * (Boot 4 / Framework 7): {@link SlackAdapter} depends on this interface so
 * unit specs can mock at this layer instead of at the underlying HTTP
 * client's fluent surface. Reference impl: {@link RestClientSlackHttpClient}.
 */
public interface SlackHttpClient {

    /**
     * POST with Bearer auth. Uses the caller-supplied content type
     * (Slack uses {@link MediaType#APPLICATION_JSON} for most methods
     * but {@link MediaType#APPLICATION_FORM_URLENCODED} for
     * {@code apps.connections.open}).
     *
     * @param url         fully-qualified Slack API URL
     * @param bearerToken bot or app token to use in the Authorization header
     * @param contentType request Content-Type
     * @param body        request body — accepts a {@code Map} (JSON) or
     *                    a {@code String} (form-urlencoded)
     * @return parsed JSON response body
     */
    JsonNode post(String url, String bearerToken, MediaType contentType, Object body);

    /**
     * Convenience — POST JSON with Bearer auth (Slack default).
     */
    default JsonNode postJson(String url, String bearerToken, Map<String, Object> body) {
        return post(url, bearerToken, MediaType.APPLICATION_JSON, body);
    }
}
