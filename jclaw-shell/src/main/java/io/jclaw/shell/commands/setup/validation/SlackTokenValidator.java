package io.jclaw.shell.commands.setup.validation;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class SlackTokenValidator {

    private final RestTemplate restTemplate;

    public SlackTokenValidator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record ValidationResult(boolean valid, String message) {}

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String botToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(botToken);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<Void> request = new HttpEntity<>(null, headers);
            Map<String, Object> response = restTemplate.postForObject(
                    "https://slack.com/api/auth.test",
                    request,
                    Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                return new ValidationResult(true, "Bot validated: " + response.get("user"));
            }
            Object error = response != null ? response.get("error") : "unknown";
            return new ValidationResult(false, "Slack API error: " + error);
        } catch (Exception e) {
            return new ValidationResult(false, "Validation failed: " + e.getMessage());
        }
    }
}
