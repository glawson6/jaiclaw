package io.jaiclaw.channel.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate;
    private final ReentrantLock lock = new ReentrantLock();

    private String cachedToken;
    private Instant expiresAt = Instant.EPOCH;

    TeamsTokenManager(String appId, String appSecret, RestTemplate restTemplate) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.restTemplate = restTemplate;
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
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", appId);
            form.add("client_secret", appSecret);
            form.add("scope", "https://api.botframework.com/.default");

            var request = new HttpEntity<>(form, headers);
            var response = restTemplate.postForEntity(TOKEN_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = MAPPER.readTree(response.getBody());
                cachedToken = body.path("access_token").asText();
                long expiresIn = body.path("expires_in").asLong(3600);
                expiresAt = Instant.now().plusSeconds(expiresIn);
                log.debug("Teams OAuth token refreshed, expires in {}s", expiresIn);
                return cachedToken;
            } else {
                throw new IllegalStateException("Token request failed: HTTP " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to refresh Teams OAuth token: {}", e.getMessage());
            throw new RuntimeException("Failed to obtain Teams access token", e);
        }
    }
}
