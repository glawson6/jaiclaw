package io.jaiclaw.channel.teams;

import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * {@link RestClient}-backed implementation of {@link TeamsHttpClient}.
 *
 * <p>Introduced at 1.0.0 with the Boot 4 / Spring Framework 7 upgrade.
 */
public final class RestClientTeamsHttpClient implements TeamsHttpClient {

    private final RestClient restClient;

    public RestClientTeamsHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public RestClientTeamsHttpClient() {
        this(RestClient.create());
    }

    @Override
    public JsonNode postJson(String url, String bearerToken, Map<String, Object> body) {
        RestClient.RequestBodySpec spec = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null && !bearerToken.isBlank()) {
            spec = spec.header("Authorization", "Bearer " + bearerToken);
        }
        return spec.body(body).retrieve().body(JsonNode.class);
    }

    @Override
    public String postForm(String url, String formBody) {
        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .body(String.class);
    }

    @Override
    public String getString(String url) {
        return restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);
    }

    @Override
    public byte[] getBytes(String url, String bearerToken) {
        RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(url);
        if (bearerToken != null && !bearerToken.isBlank()) {
            spec = spec.header("Authorization", "Bearer " + bearerToken);
        }
        return spec.retrieve().body(byte[].class);
    }
}
