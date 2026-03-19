package io.jclaw.voice.stt;

import io.jclaw.core.model.TranscriptionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * OpenAI Whisper STT provider using the /v1/audio/transcriptions endpoint.
 */
public class OpenAiSttProvider implements SttProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSttProvider.class);
    private static final String API_URL = "https://api.openai.com/v1/audio/transcriptions";

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate;

    public OpenAiSttProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model != null ? model : "whisper-1";
        this.restTemplate = new RestTemplate();
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public TranscriptionResult transcribe(byte[] audioBytes, String mimeType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(apiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("model", model);

            String extension = mimeType != null && mimeType.contains("ogg") ? ".ogg" : ".mp3";
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(mimeType != null ? mimeType : "audio/mpeg"));
            fileHeaders.setContentDispositionFormData("file", "audio" + extension);
            body.add("file", new HttpEntity<>(audioBytes, fileHeaders));

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String text = extractText(response.getBody());
                return new TranscriptionResult(text, "en", 1.0);
            }
            throw new RuntimeException("STT API returned " + response.getStatusCode());
        } catch (Exception e) {
            log.error("OpenAI STT failed: {}", e.getMessage());
            throw new RuntimeException("OpenAI STT transcription failed", e);
        }
    }

    private String extractText(String jsonResponse) {
        int textIdx = jsonResponse.indexOf("\"text\"");
        if (textIdx < 0) return jsonResponse;
        int start = jsonResponse.indexOf("\"", textIdx + 6) + 1;
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }
}
