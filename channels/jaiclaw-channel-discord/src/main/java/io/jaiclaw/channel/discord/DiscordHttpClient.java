package io.jaiclaw.channel.discord;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Abstraction over HTTP transport for Discord REST API calls.
 *
 * <p>Introduced at 1.0.0 alongside the RestTemplate → RestClient migration
 * (Boot 4 / Framework 7): {@link DiscordAdapter} depends on this interface so
 * unit specs can mock at this layer instead of at the underlying HTTP
 * client's fluent surface. Reference impl: {@link RestClientDiscordHttpClient}.
 */
public interface DiscordHttpClient {

    /**
     * POST a JSON body with a bot-token Authorization header, returning the
     * parsed JSON response.
     */
    JsonNode postJson(String url, String botToken, Map<String, Object> body);

    /**
     * GET a JSON resource with a bot-token Authorization header.
     */
    JsonNode getJson(String url, String botToken);
}
