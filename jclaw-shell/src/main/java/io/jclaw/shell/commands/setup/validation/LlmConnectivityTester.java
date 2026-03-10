package io.jclaw.shell.commands.setup.validation;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

@Component
public class LlmConnectivityTester {

    private final RestTemplate restTemplate;

    public LlmConnectivityTester(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "max_tokens", 5
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity("https://api.openai.com/v1/chat/completions", request, String.class);
        return new TestResult(true, "Connection successful");
    }

    private TestResult testAnthropic(String apiKey, String model) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "max_tokens", 5
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity("https://api.anthropic.com/v1/messages", request, String.class);
        return new TestResult(true, "Connection successful");
    }

    private TestResult testOllama(String baseUrl, String model) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "stream", false
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(baseUrl + "/api/chat", request, String.class);
        return new TestResult(true, "Connection successful");
    }
}
