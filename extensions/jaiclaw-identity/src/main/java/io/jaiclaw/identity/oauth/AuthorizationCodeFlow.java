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
 * PKCE Authorization Code flow implementation.
 * Handles authorize URL construction, code exchange, and optional userinfo fetch.
 */
public class AuthorizationCodeFlow {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationCodeFlow.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    public AuthorizationCodeFlow() {
        this(HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build());
    }

    public AuthorizationCodeFlow(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Build the authorization URL with PKCE challenge.
     */
    public String buildAuthorizeUrl(OAuthProviderConfig config,
                                     PkceGenerator.PkceChallenge pkce,
                                     String state) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", config.clientId());
        params.put("redirect_uri", config.resolvedRedirectUri());
        params.put("state", state);
        params.put("code_challenge", pkce.challenge());
        params.put("code_challenge_method", "S256");
        if (config.scopes() != null && !config.scopes().isEmpty()) {
            params.put("scope", String.join(" ", config.scopes()));
        }

        String queryString = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        return config.authorizeUrl() + "?" + queryString;
    }

    /**
     * Exchange an authorization code for tokens.
     */
    public OAuthFlowResult exchangeCode(OAuthProviderConfig config,
                                         String code,
                                         String codeVerifier) throws OAuthFlowException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("client_id", config.clientId());
        params.put("code", code);
        params.put("redirect_uri", config.resolvedRedirectUri());
        params.put("code_verifier", codeVerifier);
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
                .timeout(HTTP_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new OAuthFlowException(
                        "Token exchange failed with status " + response.statusCode() + ": " + response.body());
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
                    if (email == null) email = textOrNull(userinfo, "username");
                    accountId = textOrNull(userinfo, "sub");
                } catch (Exception e) {
                    log.debug("Failed to fetch userinfo: {}", e.getMessage());
                }
            }

            return new OAuthFlowResult(accessToken, refreshToken, expiresAt,
                    email, accountId, null, config.clientId());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new OAuthFlowException("Token exchange request failed", e);
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

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual()) ? child.asText() : null;
    }
}
