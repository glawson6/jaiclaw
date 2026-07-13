package io.jaiclaw.shell.commands.setup.validation;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class LlmConnectivityTester {

    private final RestClient restClient;

    public LlmConnectivityTester(RestClient restClient) {
        this.restClient = restClient;
    }

    public record TestResult(boolean success, String message) {}

    public TestResult test(String provider, String apiKey, String model, String ollamaBaseUrl) {
        try {
            return switch (provider) {
                case "openai" -> testOpenAi(apiKey, model);
                case "anthropic" -> testAnthropic(apiKey, model);
                case "ollama" -> testOllama(ollamaBaseUrl, model);
                default -> new TestResult(false, "Unknown provider: " + provider);
            };
        } catch (Exception e) {
            return new TestResult(false, e.getMessage());
        }
    }

    private TestResult testOpenAi(String apiKey, String model) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "max_tokens", 5
        );
        restClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        return new TestResult(true, "Connection successful");
    }

    private TestResult testAnthropic(String apiKey, String model) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "max_tokens", 5
        );
        restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .body(body)
                .retrieve()
                .toBodilessEntity();
        return new TestResult(true, "Connection successful");
    }

    private TestResult testOllama(String baseUrl, String model) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "stream", false
        );
        restClient.post()
                .uri(baseUrl + "/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        return new TestResult(true, "Connection successful");
    }
}
