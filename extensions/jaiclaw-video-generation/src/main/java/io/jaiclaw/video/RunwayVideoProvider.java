package io.jaiclaw.video;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runway Gen-3 video generation provider.
 *
 * <p>Uses Runway's REST API to submit text-to-video and image-to-video
 * generation jobs. Jobs are asynchronous — submit returns a task ID,
 * poll checks status until completion.
 *
 * @see <a href="https://docs.dev.runwayml.com/">Runway API Documentation</a>
 */
public class RunwayVideoProvider implements VideoGenerationProvider {

    private static final Logger log = LoggerFactory.getLogger(RunwayVideoProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_BASE = "https://api.dev.runwayml.com/v1";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;

    public RunwayVideoProvider(String apiKey, String model) {
        this(apiKey, model, HttpClient.newHttpClient());
    }

    // Visible for testing
    RunwayVideoProvider(String apiKey, String model, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "gen3a_turbo";
        this.httpClient = httpClient;
    }

    @Override
    public String providerId() {
        return "runway";
    }

    @Override
    public VideoGenerationResult submit(VideoGenerationRequest request) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("promptText", request.prompt());
            body.put("duration", request.durationSecs());
            body.put("ratio", mapResolutionToRatio(request.resolution()));

            if (request.imageUrl() != null && !request.imageUrl().isEmpty()) {
                body.put("promptImage", request.imageUrl());
            }

            String json = MAPPER.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/image_to_video"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Runway-Version", "2024-11-06")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode responseBody = MAPPER.readTree(response.body());
                String taskId = responseBody.path("id").asText("");
                if (taskId.isEmpty()) {
                    return VideoGenerationResult.failed("unknown", "No task ID in Runway response");
                }
                return VideoGenerationResult.queued(taskId);
            } else {
                return VideoGenerationResult.failed("unknown",
                        "Runway API error: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            log.error("Failed to submit Runway video generation job: {}", e.getMessage(), e);
            return VideoGenerationResult.failed("unknown", e.getMessage());
        }
    }

    @Override
    public VideoGenerationResult poll(String jobId) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/tasks/" + jobId))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Runway-Version", "2024-11-06")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode responseBody = MAPPER.readTree(response.body());
                String status = responseBody.path("status").asText("UNKNOWN");
                int progress = responseBody.path("progress").asInt(0);

                return switch (status.toUpperCase()) {
                    case "PENDING", "QUEUED" -> VideoGenerationResult.queued(jobId);
                    case "RUNNING", "PROCESSING" -> VideoGenerationResult.processing(jobId, progress);
                    case "SUCCEEDED", "COMPLETED" -> {
                        JsonNode output = responseBody.path("output");
                        String videoUrl = "";
                        if (output.isArray() && output.size() > 0) {
                            videoUrl = output.get(0).asText("");
                        } else if (output.isTextual()) {
                            videoUrl = output.asText("");
                        }
                        yield VideoGenerationResult.completed(jobId, videoUrl);
                    }
                    case "FAILED", "CANCELLED" -> {
                        String error = responseBody.path("failure").asText(
                                responseBody.path("failureCode").asText("Unknown error"));
                        yield VideoGenerationResult.failed(jobId, error);
                    }
                    default -> VideoGenerationResult.processing(jobId, progress);
                };
            } else {
                return VideoGenerationResult.failed(jobId,
                        "Runway poll error: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to poll Runway task {}: {}", jobId, e.getMessage(), e);
            return VideoGenerationResult.failed(jobId, e.getMessage());
        }
    }

    @Override
    public VideoCapabilities capabilities() {
        return new VideoCapabilities(
                List.of("1280x768", "768x1280", "1104x832", "832x1104"),
                10, true, true);
    }

    private String mapResolutionToRatio(String resolution) {
        if (resolution == null) return "16:9";
        return switch (resolution) {
            case "768x1280", "832x1104" -> "9:16";
            case "1280x768", "1104x832" -> "16:9";
            default -> "16:9";
        };
    }
}
