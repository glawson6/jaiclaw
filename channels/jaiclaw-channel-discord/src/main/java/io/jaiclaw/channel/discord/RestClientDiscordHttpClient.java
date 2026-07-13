package io.jaiclaw.channel.discord;

import tools.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * {@link RestClient}-backed implementation of {@link DiscordHttpClient}.
 *
 * <p>Introduced at 1.0.0 with the Boot 4 / Spring Framework 7 upgrade.
 */
public final class RestClientDiscordHttpClient implements DiscordHttpClient {

    private final RestClient restClient;

    public RestClientDiscordHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public RestClientDiscordHttpClient() {
        this(RestClient.create());
    }

    @Override
    public JsonNode postJson(String url, String botToken, Map<String, Object> body) {
        return restClient.post()
                .uri(url)
                .header("Authorization", "Bot " + botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    @Override
    public JsonNode getJson(String url, String botToken) {
        return restClient.get()
                .uri(url)
                .header("Authorization", "Bot " + botToken)
                .retrieve()
                .body(JsonNode.class);
    }
}
