package io.jaiclaw.shell.commands.setup.validation;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class DiscordTokenValidator {

    private final RestClient restClient;

    public DiscordTokenValidator(RestClient restClient) {
        this.restClient = restClient;
    }

    public record ValidationResult(boolean valid, String botUsername, String message) {}

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String botToken) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri("https://discord.com/api/v10/users/@me")
                    .header("Authorization", "Bot " + botToken)
                    .retrieve()
                    .body(Map.class);

            if (body != null && body.containsKey("username")) {
                String username = (String) body.get("username");
                return new ValidationResult(true, username, "Bot validated: " + username);
            }
            return new ValidationResult(false, null, "Discord API returned unexpected response");
        } catch (Exception e) {
            return new ValidationResult(false, null, "Validation failed: " + e.getMessage());
        }
    }
}
