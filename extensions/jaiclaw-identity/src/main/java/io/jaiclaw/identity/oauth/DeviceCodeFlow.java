package io.jaiclaw.identity.oauth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.identity.auth.CredentialStateEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OAuth Device Code flow implementation (RFC 8628).
 * Used for headless environments where a browser redirect isn't possible.
 */
public class DeviceCodeFlow {

    private static final Logger log = LoggerFactory.getLogger(DeviceCodeFlow.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    public record DeviceCodeResponse(
            String deviceCode,
            String userCode,
            String verificationUri,
            int interval,
            int expiresIn
    ) {}

    private final HttpClient httpClient;

    public DeviceCodeFlow() {
        this(HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build());
    }

    public DeviceCodeFlow(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Step 1: Request a device code from the provider.
     */
    public DeviceCodeResponse requestDeviceCode(OAuthProviderConfig config) throws OAuthFlowException {
        if (config.deviceCodeUrl() == null || config.deviceCodeUrl().isBlank()) {
            throw new OAuthFlowException("No device code URL configured for provider: " + config.providerId());
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.clientId());
        if (config.scopes() != null && !config.scopes().isEmpty()) {
            params.put("scope", String.join(" ", config.scopes()));
        }

        String body = encodeFormParams(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.deviceCodeUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new OAuthFlowException(
                        "Device code request failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode json = MAPPER.readTree(response.body());
            return new DeviceCodeResponse(
                    json.get("device_code").asText(),
                    json.get("user_code").asText(),
                    json.has("verification_uri") ? json.get("verification_uri").asText()
                            : json.get("verification_url").asText(),
                    json.has("interval") ? json.get("interval").asInt(5) : 5,
                    json.has("expires_in") ? json.get("expires_in").asInt(600) : 600
            );
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new OAuthFlowException("Device code request failed", e);
        }
    }

    /**
     * Step 2: Poll the token endpoint until the user authorizes or timeout.
     */
    public OAuthFlowResult pollForToken(OAuthProviderConfig config, DeviceCodeResponse deviceCode)
            throws OAuthFlowException {
        int intervalMs = deviceCode.interval() * 1000;
        long deadline = System.currentTimeMillis() + deviceCode.expiresIn() * 1000L;

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OAuthFlowException("Interrupted while polling for device authorization", e);
            }

            Map<String, String> params = new LinkedHashMap<>();
            params.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            params.put("device_code", deviceCode.deviceCode());
            params.put("client_id", config.clientId());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.tokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(HTTP_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(encodeFormParams(params)))
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode json = MAPPER.readTree(response.body());

                if (response.statusCode() == 200 && json.has("access_token")) {
                    String accessToken = json.get("access_token").asText();
                    String refreshToken = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
                    int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt(3600) : 3600;
                    long expiresAt = CredentialStateEvaluator.computeExpiresAt(expiresIn);

                    return new OAuthFlowResult(accessToken, refreshToken, expiresAt,
                            null, null, null, config.clientId());
                }

                String error = json.has("error") ? json.get("error").asText() : "";
                switch (error) {
                    case "authorization_pending" -> log.debug("Waiting for user authorization...");
                    case "slow_down" -> {
                        intervalMs += 5000;
                        log.debug("Slowing down polling interval to {}ms", intervalMs);
                    }
                    case "access_denied" -> throw new OAuthFlowException("User denied authorization");
                    case "expired_token" -> throw new OAuthFlowException("Device code expired");
                    default -> {
                        if (response.statusCode() >= 400) {
                            log.debug("Token poll error: {} (status {})", error, response.statusCode());
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new OAuthFlowException("Token poll request failed", e);
            }
        }

        throw new OAuthFlowException("Device code polling timed out");
    }

    private String encodeFormParams(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
