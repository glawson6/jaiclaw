package io.jclaw.voice.tts;

import io.jclaw.core.model.AudioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * OpenAI TTS provider using the /v1/audio/speech endpoint.
 */
public class OpenAiTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTtsProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/audio/speech";

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;

    public OpenAiTtsProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "tts-1";
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public AudioResult synthesize(String text, String voice, Map<String, String> options) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            String body = String.format(
                    "{\"model\":\"%s\",\"input\":\"%s\",\"voice\":\"%s\",\"response_format\":\"mp3\"}",
                    model, escapeJson(text), voice != null ? voice : "alloy");

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST, request, byte[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return new AudioResult(response.getBody(), "audio/mpeg", 0);
            }
            throw new RuntimeException("TTS API returned " + response.getStatusCode());
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
