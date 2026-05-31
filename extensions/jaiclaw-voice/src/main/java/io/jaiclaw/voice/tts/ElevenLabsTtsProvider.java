package io.jaiclaw.voice.tts;

import io.jaiclaw.core.model.AudioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * ElevenLabs TTS provider using the ElevenLabs Text-to-Speech API.
 *
 * <p>Sends text to the ElevenLabs {@code /v1/text-to-speech/{voice_id}} endpoint
 * and returns MP3 audio bytes. Supports voice selection and model configuration.
 *
 * @see <a href="https://docs.elevenlabs.io/api-reference/text-to-speech">ElevenLabs API</a>
 */
public class ElevenLabsTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsTtsProvider.class);
    private static final String API_BASE = "https://api.elevenlabs.io/v1";

    private final String apiKey;
    private final String defaultVoiceId;
    private final String modelId;
    private final HttpClient httpClient;

    public ElevenLabsTtsProvider(String apiKey, String defaultVoiceId, String modelId) {
        this(apiKey, defaultVoiceId, modelId, HttpClient.newHttpClient());
    }

    // Visible for testing
    ElevenLabsTtsProvider(String apiKey, String defaultVoiceId, String modelId, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.defaultVoiceId = defaultVoiceId != null ? defaultVoiceId : "21m00Tcm4TlvDq8ikWAM";
        this.modelId = modelId != null ? modelId : "eleven_monolingual_v1";
        this.httpClient = httpClient;
    }

    @Override
    public String providerId() {
        return "elevenlabs";
    }

    @Override
    public AudioResult synthesize(String text, String voice, Map<String, String> options) {
        try {
            String voiceId = resolveVoiceId(voice, options);
            String model = options.getOrDefault("model", modelId);

            String json = "{\"text\":\"" + escapeJson(text) + "\","
                    + "\"model_id\":\"" + model + "\","
                    + "\"voice_settings\":{\"stability\":0.5,\"similarity_boost\":0.75}}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/text-to-speech/" + voiceId))
                    .header("Content-Type", "application/json")
                    .header("xi-api-key", apiKey)
                    .header("Accept", "audio/mpeg")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                byte[] audioData = response.body();
                return new AudioResult(audioData, "audio/mpeg", 0);
            }

            throw new RuntimeException("ElevenLabs API error: HTTP " + response.statusCode()
                    + " - " + new String(response.body()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ElevenLabs TTS failed: {}", e.getMessage(), e);
            throw new RuntimeException("ElevenLabs TTS synthesis failed", e);
        }
    }

    /**
     * Resolves the voice ID from the voice name or options.
     * If the voice looks like an ElevenLabs voice ID (alphanumeric, 20 chars), use it directly.
     * Otherwise, fall back to the configured default voice ID.
     */
    private String resolveVoiceId(String voice, Map<String, String> options) {
        String voiceIdOverride = options.get("voice_id");
        if (voiceIdOverride != null && !voiceIdOverride.isBlank()) {
            return voiceIdOverride;
        }
        if (voice != null && voice.length() >= 15 && voice.matches("[a-zA-Z0-9]+")) {
            return voice;
        }
        return defaultVoiceId;
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
