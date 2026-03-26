package io.jaiclaw.channel.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates inbound JWT tokens from the Bot Framework using JDK-native
 * {@code java.security} RSA — zero external dependencies.
 *
 * <p>Fetches the OpenID configuration and JWKS keys from the Bot Framework,
 * caches public keys for 24 hours, and validates RS256 signatures.
 */
class TeamsJwtValidator {

    private static final Logger log = LoggerFactory.getLogger(TeamsJwtValidator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String OPENID_CONFIG_URL =
            "https://login.botframework.com/v1/.well-known/openidconfiguration";
    private static final long KEY_CACHE_DURATION_SECONDS = 86400; // 24 hours
    private static final long CLOCK_SKEW_SECONDS = 300; // 5 minutes

    private final String expectedAudience;
    private final RestTemplate restTemplate;

    private final Map<String, RSAPublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile Instant keyCacheExpiry = Instant.EPOCH;
    private volatile String jwksUri;

    TeamsJwtValidator(String expectedAudience, RestTemplate restTemplate) {
        this.expectedAudience = expectedAudience;
        this.restTemplate = restTemplate;
    }

    /**
     * Validates the JWT token from the Authorization header.
     * Returns true if the token is valid, false otherwise.
     */
    boolean validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("JWT does not have 3 parts");
                return false;
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            JsonNode header = MAPPER.readTree(headerJson);
            JsonNode payload = MAPPER.readTree(payloadJson);

            // Verify algorithm
            String alg = header.path("alg").asText();
            if (!"RS256".equals(alg)) {
                log.warn("Unsupported JWT algorithm: {}", alg);
                return false;
            }

            // Verify audience
            String aud = payload.path("aud").asText();
            if (!expectedAudience.equals(aud)) {
                log.warn("JWT audience mismatch: expected={}, got={}", expectedAudience, aud);
                return false;
            }

            // Verify expiry and not-before with clock skew tolerance
            Instant now = Instant.now();
            long exp = payload.path("exp").asLong(0);
            long nbf = payload.path("nbf").asLong(0);

            if (exp > 0 && now.isAfter(Instant.ofEpochSecond(exp).plusSeconds(CLOCK_SKEW_SECONDS))) {
                log.warn("JWT expired");
                return false;
            }
            if (nbf > 0 && now.isBefore(Instant.ofEpochSecond(nbf).minusSeconds(CLOCK_SKEW_SECONDS))) {
                log.warn("JWT not yet valid (nbf)");
                return false;
            }

            // Verify RSA signature
            String kid = header.path("kid").asText();
            RSAPublicKey publicKey = getPublicKey(kid);
            if (publicKey == null) {
                log.warn("No public key found for kid={}", kid);
                return false;
            }

            byte[] signedContent = (parts[0] + "." + parts[1]).getBytes();
            var signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signedContent);

            if (!signature.verify(signatureBytes)) {
                log.warn("JWT signature verification failed");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    private RSAPublicKey getPublicKey(String kid) {
        // Try cache first
        RSAPublicKey cached = keyCache.get(kid);
        if (cached != null && Instant.now().isBefore(keyCacheExpiry)) {
            return cached;
        }

        // Refresh keys
        refreshKeys();
        return keyCache.get(kid);
    }

    private synchronized void refreshKeys() {
        if (Instant.now().isBefore(keyCacheExpiry)) {
            return; // another thread already refreshed
        }

        try {
            // Step 1: Get JWKS URI from OpenID configuration
            if (jwksUri == null) {
                String configJson = restTemplate.getForObject(OPENID_CONFIG_URL, String.class);
                JsonNode config = MAPPER.readTree(configJson);
                jwksUri = config.path("jwks_uri").asText();
                log.debug("Teams JWKS URI: {}", jwksUri);
            }

            // Step 2: Fetch JWKS keys
            String jwksJson = restTemplate.getForObject(jwksUri, String.class);
            JsonNode jwks = MAPPER.readTree(jwksJson);
            JsonNode keys = jwks.path("keys");

            Map<String, RSAPublicKey> newKeys = new ConcurrentHashMap<>();
            for (JsonNode key : keys) {
                if (!"RSA".equals(key.path("kty").asText())) continue;

                String kid = key.path("kid").asText();
                String n = key.path("n").asText();
                String e = key.path("e").asText();

                byte[] nBytes = Base64.getUrlDecoder().decode(n);
                byte[] eBytes = Base64.getUrlDecoder().decode(e);

                RSAPublicKeySpec spec = new RSAPublicKeySpec(
                        new BigInteger(1, nBytes),
                        new BigInteger(1, eBytes));
                RSAPublicKey rsaKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
                newKeys.put(kid, rsaKey);
            }

            keyCache.clear();
            keyCache.putAll(newKeys);
            keyCacheExpiry = Instant.now().plusSeconds(KEY_CACHE_DURATION_SECONDS);
            log.debug("Refreshed {} Teams JWKS keys", newKeys.size());

        } catch (Exception e) {
            log.error("Failed to refresh Teams JWKS keys: {}", e.getMessage());
            // On failure, reset jwksUri so next attempt re-fetches OpenID config
            jwksUri = null;
        }
    }
}
