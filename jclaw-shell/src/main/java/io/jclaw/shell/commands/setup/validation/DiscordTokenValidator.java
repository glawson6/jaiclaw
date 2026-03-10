package io.jclaw.shell.commands.setup.validation;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class DiscordTokenValidator {

    private final RestTemplate restTemplate;

    public DiscordTokenValidator(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public record ValidationResult(boolean valid, String botUsername, String message) {}

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String botToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bot " + botToken);

            HttpEntity<Void> request = new HttpEntity<>(null, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://discord.com/api/v10/users/@me",
                    HttpMethod.GET,
                    request,
                    Map.class);

            Map<String, Object> body = response.getBody();
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
