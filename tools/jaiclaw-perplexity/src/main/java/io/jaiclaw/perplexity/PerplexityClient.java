package io.jaiclaw.perplexity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.jaiclaw.perplexity.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class PerplexityClient {

    private static final Logger log = LoggerFactory.getLogger(PerplexityClient.class);
    private static final String BASE_URL = "https://api.perplexity.ai";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PerplexityClient(String apiKey) {
        this(apiKey, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build());
    }

    PerplexityClient(String apiKey, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    ObjectMapper mapper() {
        return mapper;
    }

    public SonarResponse chat(SonarRequest request) {
        Map<String, Object> body = buildSonarBody(request, false);
        String json = post("/chat/completions", body);
        return parse(json, SonarResponse.class);
    }

    public Stream<String> chatStream(SonarRequest request) {
        Map<String, Object> body = buildSonarBody(request, true);
        String jsonBody = serialize(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body().readAllBytes());
                throw new PerplexityApiException(response.statusCode(),
                        "Perplexity API error: " + response.statusCode(), errorBody);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()));
            return reader.lines()
                    .filter(line -> line.startsWith("data: "))
                    .map(line -> line.substring(6))
                    .filter(data -> !data.equals("[DONE]"))
                    .map(data -> {
                        try {
                            var node = mapper.readTree(data);
                            var choices = node.path("choices");
                            if (choices.isArray() && !choices.isEmpty()) {
                                var delta = choices.get(0).path("delta").path("content");
                                if (!delta.isMissingNode() && !delta.isNull()) {
                                    return delta.asText();
                                }
                            }
                            return "";
                        } catch (JsonProcessingException e) {
                            log.warn("Failed to parse SSE chunk: {}", data, e);
                            return "";
                        }
                    })
                    .filter(s -> !s.isEmpty());
        } catch (PerplexityApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new PerplexityApiException(0, "Failed to stream from Perplexity: " + e.getMessage(), "");
        }
    }

    public SearchApiResponse search(SearchApiRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", request.query());
        if (request.numResults() != null) body.put("num_results", request.numResults());
        if (request.recencyFilter() != null) body.put("recency_filter", request.recencyFilter());
        if (request.domainFilter() != null && !request.domainFilter().isEmpty()) {
            body.put("domain_filter", request.domainFilter());
        }

        String json = post("/search", body);
        return parse(json, SearchApiResponse.class);
    }

    public AgentResponse agent(AgentRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("preset", request.preset());
        if (request.model() != null) body.put("model", request.model());
        body.put("messages", request.messages());
        if (request.maxTokens() != null) body.put("max_tokens", request.maxTokens());
        if (request.tools() != null && !request.tools().isEmpty()) body.put("tools", request.tools());

        String json = post("/v1/agent", body);
        return parse(json, AgentResponse.class);
    }

    private Map<String, Object> buildSonarBody(SonarRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", request.messages());
        body.put("stream", stream);
        if (request.temperature() != null) body.put("temperature", request.temperature());
        if (request.maxTokens() != null) body.put("max_tokens", request.maxTokens());
        if (request.searchDomainFilter() != null && !request.searchDomainFilter().isEmpty()) {
            body.put("search_domain_filter", request.searchDomainFilter());
        }
        if (request.searchRecencyFilter() != null) {
            body.put("search_recency_filter", request.searchRecencyFilter());
        }
        if (request.returnImages()) body.put("return_images", true);
        if (request.returnRelatedQuestions()) body.put("return_related_questions", true);
        return body;
    }

    private String post(String path, Map<String, Object> body) {
        String jsonBody = serialize(body);
        log.debug("POST {} body={}", path, jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(120))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("Response {}: {}", response.statusCode(), response.body());

            if (response.statusCode() >= 400) {
                throw new PerplexityApiException(response.statusCode(),
                        "Perplexity API error: " + response.statusCode(), response.body());
            }
            return response.body();
        } catch (PerplexityApiException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new PerplexityApiException(0, "Failed to call Perplexity: " + e.getMessage(), "");
        }
    }

    private String serialize(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize request", e);
        }
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new PerplexityApiException(0, "Failed to parse response: " + e.getMessage(), json);
        }
    }
}
