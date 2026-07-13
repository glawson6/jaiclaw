package io.jaiclaw.voice.tts;

import io.jaiclaw.core.model.AudioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * OpenAI TTS provider using the /v1/audio/speech endpoint.
 */
public class OpenAiTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTtsProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/audio/speech";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    public OpenAiTtsProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "tts-1";
        this.restClient = RestClient.create();
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public AudioResult synthesize(String text, String voice, Map<String, String> options) {
        try {
            String body = String.format(
                    "{\"model\":\"%s\",\"input\":\"%s\",\"voice\":\"%s\",\"response_format\":\"mp3\"}",
                    model, escapeJson(text), voice != null ? voice : "alloy");

            byte[] audio = restClient.post()
                    .uri(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);

            if (audio != null && audio.length > 0) {
                return new AudioResult(audio, "audio/mpeg", 0);
            }
            throw new RuntimeException("TTS API returned empty body");
        } catch (Exception e) {
            log.error("OpenAI TTS failed: {}", e.getMessage());
            throw new RuntimeException("OpenAI TTS synthesis failed", e);
        }
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
