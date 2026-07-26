package io.jaiclaw.channel.signal;

import tools.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

/**
 * {@link RestClient}-backed implementation of {@link SignalHttpClient}.
 *
 * <p>Introduced at 1.0.0 with the Boot 4 / Spring Framework 7 upgrade
 * (RestTemplate → RestClient).
 */
public final class RestClientSignalHttpClient implements SignalHttpClient {

    private final RestClient restClient;

    public RestClientSignalHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public RestClientSignalHttpClient() {
        this(RestClient.create());
    }

    @Override
    public JsonNode getJson(String url) {
        return restClient.get()
                .uri(url)
                .retrieve()
                .body(JsonNode.class);
    }

    @Override
    public JsonNode postJson(String url, Object body) {
        return restClient.post()
                .uri(url)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }
}
