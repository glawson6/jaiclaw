package io.jaiclaw.channel.slack;

import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link RestClient}-backed implementation of {@link SlackHttpClient}.
 *
 * <p>Introduced at 1.0.0 with the Boot 4 / Spring Framework 7 upgrade.
 */
public final class RestClientSlackHttpClient implements SlackHttpClient {

    private final RestClient restClient;

    public RestClientSlackHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public RestClientSlackHttpClient() {
        this(RestClient.create());
    }

    @Override
    public JsonNode post(String url, String bearerToken, MediaType contentType, Object body) {
        return restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(contentType)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }
}
