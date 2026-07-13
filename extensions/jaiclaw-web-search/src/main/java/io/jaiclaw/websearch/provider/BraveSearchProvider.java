package io.jaiclaw.websearch.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.http.ProxyAwareHttpClientFactory;
import io.jaiclaw.websearch.WebSearchProvider;
import io.jaiclaw.websearch.WebSearchResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Brave Search API provider. Requires a Brave Search API key.
 */
public class BraveSearchProvider implements WebSearchProvider {

    private static final String BASE_URL = "https://api.search.brave.com/res/v1/web/search";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final HttpClient httpClient;

    public BraveSearchProvider(String apiKey) {
        this(apiKey, ProxyAwareHttpClientFactory.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public BraveSearchProvider(String apiKey, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
    }

    @Override
    public String id() {
        return "brave";
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = BASE_URL + "?q=" + encoded + "&count=" + maxResults;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("X-Subscription-Token", apiKey)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Brave search failed: HTTP " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode webResults = root.path("web").path("results");

        List<WebSearchResult> results = new ArrayList<>();
        for (JsonNode node : webResults) {
            results.add(new WebSearchResult(
                    node.path("title").asText(""),
                    node.path("url").asText(""),
                    node.path("description").asText("")
            ));
        }
        return results;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
