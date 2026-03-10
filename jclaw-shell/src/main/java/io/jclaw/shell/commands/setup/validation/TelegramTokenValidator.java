package io.jclaw.shell.commands.setup.validation;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class TelegramTokenValidator {

    private final RestTemplate restTemplate;

    public TelegramTokenValidator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record ValidationResult(boolean valid, String botUsername, String message) {}

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String botToken) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    "https://api.telegram.org/bot{token}/getMe",
                    Map.class,
                    botToken);
            if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
                Map<String, Object> resultMap = (Map<String, Object>) response.get("result");
                String username = (String) resultMap.get("username");
                return new ValidationResult(true, username, "Bot validated: @" + username);
            }
            return new ValidationResult(false, null, "Telegram API returned unexpected response");
        } catch (Exception e) {
            return new ValidationResult(false, null, "Validation failed: " + e.getMessage());
        }
    }
}
