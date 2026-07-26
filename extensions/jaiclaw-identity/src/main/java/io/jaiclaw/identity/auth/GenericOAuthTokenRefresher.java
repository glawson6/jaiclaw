package io.jaiclaw.identity.auth;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.auth.OAuthCredential;
import io.jaiclaw.identity.oauth.OAuthProviderConfig;
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
 * Standard OAuth token refresh using the {@code refresh_token} grant type.
 * Works with any provider that implements RFC 6749 Section 6.
 */
public class GenericOAuthTokenRefresher implements TokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(GenericOAuthTokenRefresher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final OAuthProviderConfig config;
    private final HttpClient httpClient;

    public GenericOAuthTokenRefresher(OAuthProviderConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build());
    }

    public GenericOAuthTokenRefresher(OAuthProviderConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public String providerId() {
        return config.providerId();
    }

    @Override
    public OAuthCredential refresh(OAuthCredential current) throws TokenRefreshException {
        String clientId = current.clientId() != null ? current.clientId() : config.clientId();
        if (clientId == null || clientId.isBlank()) {
            throw new TokenRefreshException(config.providerId(),
                    "No client ID available for refresh (neither in credential nor provider config)");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", current.refresh());
        params.put("client_id", clientId);
        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            params.put("client_secret", config.clientSecret());
        }

        String body = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.tokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new TokenRefreshException(config.providerId(),
                        "Token refresh failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode json = MAPPER.readTree(response.body());
            String newAccess = json.has("access_token") ? json.get("access_token").asText() : null;
            if (newAccess == null || newAccess.isBlank()) {
                throw new TokenRefreshException(config.providerId(),
                        "Token refresh response missing access_token");
            }

            // Per RFC 6749 §6: if server returns new refresh_token, use it; else keep old
            String newRefresh = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
            int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt(3600) : 3600;
            long newExpires = CredentialStateEvaluator.computeExpiresAt(expiresIn);

            log.debug("Refreshed OAuth token for provider '{}', new expiry in {}s",
                    config.providerId(), expiresIn);

            return current.withRefreshedTokens(newAccess, newRefresh, newExpires);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new TokenRefreshException(config.providerId(), "Token refresh request failed", e);
        }
    }
}
