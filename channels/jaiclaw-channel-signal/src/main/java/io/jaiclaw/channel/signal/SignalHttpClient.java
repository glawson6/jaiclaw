package io.jaiclaw.channel.signal;

import tools.jackson.databind.JsonNode;

/**
 * Abstraction over HTTP transport for signal-cli-rest-api calls.
 *
 * <p>Introduced at 1.0.0 alongside the RestTemplate → RestClient migration
 * (Boot 4 / Framework 7): {@link SignalAdapter} depends on this interface
 * so unit specs can mock at this layer instead of at the underlying HTTP
 * client's fluent surface. The reference implementation
 * {@link RestClientSignalHttpClient} wraps a {@link
 * org.springframework.web.client.RestClient}.
 */
public interface SignalHttpClient {

    /**
     * Execute a GET request and parse the response as JSON.
     *
     * @param url fully-qualified signal-cli-rest-api URL
     * @return parsed JSON response body, or null on non-2xx / empty body
     */
    JsonNode getJson(String url);

    /**
     * Execute a POST request with a JSON body and parse the response as JSON.
     *
     * @param url  fully-qualified signal-cli-rest-api URL
     * @param body request body (will be serialized to JSON)
     * @return parsed JSON response body
     * @throws org.springframework.web.client.RestClientException on non-2xx
     */
    JsonNode postJson(String url, Object body);
}
