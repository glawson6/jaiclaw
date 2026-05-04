package io.jaiclaw.identity.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Resource Owner Password Credentials (ROPC) flow implementation (RFC 6749 §4.3).
 * Sends username + password directly to the token endpoint.
 * Used for trusted CLI applications where browser-based flows are impractical.
 */
public class ResourceOwnerPasswordFlow {

    private static final Logger log = LoggerFactory.getLogger(ResourceOwnerPasswordFlow.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public ResourceOwnerPasswordFlow() {
        this(HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build());
    }

    public ResourceOwnerPasswordFlow(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Exchange username and password for tokens.
     *
     * @param config   the provider configuration (must have tokenUrl, clientId)
     * @param username the resource owner's username
     * @param password the resource owner's password
     * @return the OAuth flow result with access token, refresh token, etc.
     * @throws OAuthFlowException if the token exchange fails
     */
    public OAuthFlowResult requestToken(OAuthProviderConfig config, String username, String password)
            throws OAuthFlowException {
        if (config.tokenUrl() == null || config.tokenUrl().isBlank()) {
            throw new OAuthFlowException("No token URL configured for provider: " + config.providerId());
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "password");
        params.put("client_id", config.clientId());
        params.put("username", username);
        params.put("password", password);

        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            params.put("client_secret", config.clientSecret());
        }
        if (config.scopes() != null && !config.scopes().isEmpty()) {
            params.put("scope", String.join(" ", config.scopes()));
        }

        String body = encodeFormParams(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.tokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new OAuthFlowException(
                        "ROPC token request failed with status " + response.statusCode() + ": " + response.body());
            }

            JsonNode json = MAPPER.readTree(response.body());
            String accessToken = json.has("access_token") ? json.get("access_token").asText() : null;
            String refreshToken = json.has("refresh_token") ? json.get("refresh_token").asText() : null;
            int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt(3600) : 3600;
            long expiresAt = CredentialStateEvaluator.computeExpiresAt(expiresIn);

            if (accessToken == null || accessToken.isBlank()) {
                throw new OAuthFlowException("Token response missing access_token");
            }

            // Fetch userinfo if endpoint is configured
            String email = null;
            String accountId = null;
            if (config.userinfoUrl() != null && !config.userinfoUrl().isBlank()) {
                try {
                    JsonNode userinfo = fetchUserinfo(config.userinfoUrl(), accessToken);
                    email = textOrNull(userinfo, "email");
                    if (email == null) email = textOrNull(userinfo, "preferred_username");
                    if (email == null) email = username;
                    accountId = textOrNull(userinfo, "sub");
                } catch (Exception e) {
                    log.debug("Failed to fetch userinfo: {}", e.getMessage());
                    email = username;
                }
            } else {
                email = username;
            }

            return new OAuthFlowResult(accessToken, refreshToken, expiresAt,
                    email, accountId, null, config.clientId());

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new OAuthFlowException("ROPC token request failed", e);
        }
    }

    private JsonNode fetchUserinfo(String userinfoUrl, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(userinfoUrl))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return MAPPER.readTree(response.body());
    }

    private String encodeFormParams(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }
}
