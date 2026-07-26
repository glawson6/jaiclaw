package io.jaiclaw.channel.teams;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Manages OAuth 2.0 tokens for outbound Bot Framework REST API calls.
 *
 * <p>Uses the {@code client_credentials} grant against the Azure AD v2.0 endpoint
 * to obtain bearer tokens. Tokens are cached and proactively refreshed 5 minutes
 * before expiry.
 */
class TeamsTokenManager {

    private static final Logger log = LoggerFactory.getLogger(TeamsTokenManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/botframework.com/oauth2/v2.0/token";
    private static final long REFRESH_MARGIN_SECONDS = 300; // refresh 5 min before expiry

    private final String appId;
    private final String appSecret;
    private final TeamsHttpClient httpClient;
    private final ReentrantLock lock = new ReentrantLock();

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    TeamsTokenManager(String appId, String appSecret, TeamsHttpClient httpClient) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.httpClient = httpClient;
    }

    /**
     * Returns a valid access token, refreshing if necessary.
     */
    String getAccessToken() {
        if (cachedToken != null && Instant.now().plusSeconds(REFRESH_MARGIN_SECONDS).isBefore(expiresAt)) {
            return cachedToken;
        }

        lock.lock();
        try {
            // Double-check after acquiring lock
            if (cachedToken != null && Instant.now().plusSeconds(REFRESH_MARGIN_SECONDS).isBefore(expiresAt)) {
                return cachedToken;
            }
            return refreshToken();
        } finally {
            lock.unlock();
        }
    }

    private String refreshToken() {
        try {
            String formBody = "grant_type=client_credentials"
                    + "&client_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode("https://api.botframework.com/.default", StandardCharsets.UTF_8);

            String responseBody = httpClient.postForm(TOKEN_URL, formBody);
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("Token request returned empty response");
            }
            JsonNode body = MAPPER.readTree(responseBody);
            cachedToken = body.path("access_token").asText();
            long expiresIn = body.path("expires_in").asLong(3600);
            expiresAt = Instant.now().plusSeconds(expiresIn);
            log.debug("Teams OAuth token refreshed, expires in {}s", expiresIn);
            return cachedToken;
        } catch (Exception e) {
            log.error("Failed to refresh Teams OAuth token: {}", e.getMessage());
            throw new RuntimeException("Failed to obtain Teams access token", e);
        }
    }
}
