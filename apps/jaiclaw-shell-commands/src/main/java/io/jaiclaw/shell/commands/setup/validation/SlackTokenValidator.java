package io.jaiclaw.shell.commands.setup.validation;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SlackTokenValidator {

    private final RestClient restClient;

    public SlackTokenValidator(RestClient restClient) {
        this.restClient = restClient;
    }

    public record ValidationResult(boolean valid, String message) {}

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String botToken) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://slack.com/api/auth.test")
                    .header("Authorization", "Bearer " + botToken)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve()
                    .body(Map.class);

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
