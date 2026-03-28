package io.jaiclaw.config.prompt;

import io.jaiclaw.config.SystemPromptConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Loads system prompt via HTTP GET from the URL specified by {@link SystemPromptConfig#source()}.
 */
public class UrlSystemPromptLoader implements SystemPromptLoader {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Override
    public boolean supports(String strategy) {
        return "url".equalsIgnoreCase(strategy);
    }

    @Override
    public String load(SystemPromptConfig config) {
        String source = config.source();
        if (source == null || source.isBlank()) {
            throw new SystemPromptLoadException("URL strategy requires a 'source' URL");
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new SystemPromptLoadException(
                        "HTTP " + response.statusCode() + " fetching prompt from: " + source);
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SystemPromptLoadException("Failed to fetch prompt from URL: " + source, e);
        }
    }
}
