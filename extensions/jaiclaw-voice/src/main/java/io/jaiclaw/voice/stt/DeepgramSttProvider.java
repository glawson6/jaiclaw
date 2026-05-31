package io.jaiclaw.voice.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.model.TranscriptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Deepgram STT provider using the Deepgram pre-recorded transcription API.
 *
 * <p>Sends audio bytes to the Deepgram {@code /v1/listen} endpoint and
 * returns the transcribed text with language detection and confidence scores.
 *
 * @see <a href="https://developers.deepgram.com/reference/pre-recorded">Deepgram API</a>
 */
public class DeepgramSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepgramSttProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_URL = "https://api.deepgram.com/v1/listen";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;

    public DeepgramSttProvider(String apiKey, String model) {
        this(apiKey, model, HttpClient.newHttpClient());
    }

    // Visible for testing
    DeepgramSttProvider(String apiKey, String model, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "nova-2";
        this.httpClient = httpClient;
    }

    @Override
    public String providerId() {
        return "deepgram";
    }

    @Override
    public TranscriptionResult transcribe(byte[] audioBytes, String mimeType) {
        try {
            String contentType = mimeType != null ? mimeType : "audio/mpeg";
            String queryParams = "?model=" + model + "&detect_language=true&punctuate=true";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + queryParams))
                    .header("Authorization", "Token " + apiKey)
                    .header("Content-Type", contentType)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audioBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseResponse(response.body());
            }

            throw new RuntimeException("Deepgram API error: HTTP " + response.statusCode()
                    + " - " + response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Deepgram STT failed: {}", e.getMessage(), e);
            throw new RuntimeException("Deepgram STT transcription failed", e);
        }
    }

    private TranscriptionResult parseResponse(String jsonBody) {
        try {
            JsonNode root = MAPPER.readTree(jsonBody);
            JsonNode results = root.path("results");
            JsonNode channels = results.path("channels");

            if (!channels.isArray() || channels.isEmpty()) {
                return new TranscriptionResult("", "en", 0.0);
            }

            JsonNode firstChannel = channels.get(0);
            JsonNode alternatives = firstChannel.path("alternatives");

            if (!alternatives.isArray() || alternatives.isEmpty()) {
                return new TranscriptionResult("", "en", 0.0);
            }

            JsonNode bestAlternative = alternatives.get(0);
            String transcript = bestAlternative.path("transcript").asText("");
            double confidence = bestAlternative.path("confidence").asDouble(0.0);

            // Deepgram returns detected language in the channel metadata
            String language = firstChannel.path("detected_language").asText(
                    results.path("channels").get(0).path("detected_language").asText("en"));

            return new TranscriptionResult(transcript, language, confidence);
        } catch (Exception e) {
            log.warn("Failed to parse Deepgram response, returning raw: {}", e.getMessage());
            return new TranscriptionResult(jsonBody, "en", 0.0);
        }
    }
}
