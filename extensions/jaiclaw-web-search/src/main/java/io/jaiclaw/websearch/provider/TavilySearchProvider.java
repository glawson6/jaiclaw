package io.jaiclaw.websearch.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.http.ProxyAwareHttpClientFactory;
import io.jaiclaw.websearch.WebSearchProvider;
import io.jaiclaw.websearch.WebSearchResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tavily Search API provider. Requires a Tavily API key.
 */
public class TavilySearchProvider implements WebSearchProvider {

    private static final String BASE_URL = "https://api.tavily.com/search";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final HttpClient httpClient;

    public TavilySearchProvider(String apiKey) {
        this(apiKey, ProxyAwareHttpClientFactory.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public TavilySearchProvider(String apiKey, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
    }

    @Override
    public String id() {
        return "tavily";
    }

    @Override
    public List<WebSearchResult> search(String query, int maxResults) throws Exception {
        Map<String, Object> body = Map.of(
                "api_key", apiKey,
                "query", query,
                "max_results", maxResults
        );
        String jsonBody = MAPPER.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Tavily search failed: HTTP " + response.statusCode());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode resultsNode = root.path("results");

        List<WebSearchResult> results = new ArrayList<>();
        for (JsonNode node : resultsNode) {
            results.add(new WebSearchResult(
                    node.path("title").asText(""),
                    node.path("url").asText(""),
                    node.path("content").asText("")
            ));
        }
        return results;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
