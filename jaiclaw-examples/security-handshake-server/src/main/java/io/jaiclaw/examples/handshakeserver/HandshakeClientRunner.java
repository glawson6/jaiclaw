package io.jaiclaw.examples.handshakeserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.tools.security.CryptoService;
import io.jaiclaw.tools.security.HandshakeHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Programmatic client that performs the full 7-step security handshake flow
 * on application startup, then calls a protected MCP tool with the resulting
 * Bearer token.
 *
 * <p>Uses {@link HandshakeHttpClient} for HTTP communication and
 * {@link CryptoService} for local cryptographic operations (key generation,
 * ECDH key agreement, HMAC signing).
 */
@Component
public class HandshakeClientRunner {

    private static final Logger log = LoggerFactory.getLogger(HandshakeClientRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CryptoService cryptoService;

    public HandshakeClientRunner(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runHandshake() {
        log.info("========================================");
        log.info("  Security Handshake Client Demo");
        log.info("========================================");

        try {
            var client = new HandshakeHttpClient("http://localhost:8080/mcp/security");

            // Step 1: Query server capabilities
            log.info("[Step 1/7] Querying server capabilities...");
            String capsJson = client.post("/capabilities", Map.of());
            JsonNode caps = MAPPER.readTree(capsJson);
            String cipherSuite = caps.get("cipherSuites").get(1).asText(); // ECDH-P256
            String authMethod = caps.get("authMethods").get(0).asText();   // HMAC-SHA256
            log.info("  Server supports: cipherSuites={}, authMethods={}, bootstrap={}",
                    caps.get("cipherSuites"), caps.get("authMethods"), caps.get("bootstrapTrust"));

            // Step 2: Generate client key pair
            log.info("[Step 2/7] Generating client EC P-256 key pair...");
            KeyPair clientKeyPair = cryptoService.generateP256KeyPair();
            String clientPublicKeyEncoded = cryptoService.encodePublicKey(clientKeyPair.getPublic());
            log.info("  Client public key: {}...", clientPublicKeyEncoded.substring(0, 20));

            // Step 3: Negotiate (ECDH key exchange + bootstrap with API key)
            log.info("[Step 3/7] Negotiating key exchange with API key bootstrap...");
            Map<String, Object> negotiatePayload = new LinkedHashMap<>();
            negotiatePayload.put("clientPublicKey", clientPublicKeyEncoded);
            negotiatePayload.put("cipherSuite", cipherSuite);
            negotiatePayload.put("authMethod", authMethod);
            negotiatePayload.put("clientId", "demo-client");
            negotiatePayload.put("apiKey", "demo-api-key-12345");
            String negJson = client.post("/negotiate", negotiatePayload);
            JsonNode neg = MAPPER.readTree(negJson);
            String handshakeId = neg.get("handshakeId").asText();
            String serverPublicKeyEncoded = neg.get("serverPublicKey").asText();
            log.info("  Handshake ID: {}", handshakeId);
            log.info("  Key fingerprint: {}", neg.get("keyFingerprint").asText());

            // Step 4: ECDH key agreement (client side)
            log.info("[Step 4/7] Performing ECDH key agreement...");
            PublicKey serverPublicKey = cryptoService.decodePublicKey(serverPublicKeyEncoded, "EC");
            byte[] sharedSecret = cryptoService.keyAgreement(
                    clientKeyPair.getPrivate(), serverPublicKey, "ECDH");
            String fingerprint = cryptoService.sha256Fingerprint(sharedSecret);
            log.info("  Shared secret fingerprint: {}", fingerprint);
            log.info("  Fingerprint match: {}", fingerprint.equals(neg.get("keyFingerprint").asText()));

            // Step 5: Request challenge
            log.info("[Step 5/7] Requesting challenge nonce...");
            String chalJson = client.post("/challenge", Map.of("handshakeId", handshakeId));
            JsonNode chal = MAPPER.readTree(chalJson);
            String challengeNonce = chal.get("challenge").asText();
            log.info("  Challenge nonce: {}...", challengeNonce.substring(0, 20));

            // Step 6: Sign challenge and verify
            log.info("[Step 6/7] Signing challenge with HMAC-SHA256...");
            String signature = cryptoService.hmacSign(sharedSecret, challengeNonce);
            Map<String, Object> verifyPayload = new LinkedHashMap<>();
            verifyPayload.put("handshakeId", handshakeId);
            verifyPayload.put("method", "HMAC-SHA256");
            verifyPayload.put("credential", signature);
            String verJson = client.post("/verify", verifyPayload);
            JsonNode ver = MAPPER.readTree(verJson);
            log.info("  Verified: {}, subject: {}", ver.get("verified").asBoolean(), ver.get("subject").asText());

            // Step 7: Establish session
            log.info("[Step 7/7] Establishing session...");
            String estJson = client.post("/establish", Map.of("handshakeId", handshakeId));
            JsonNode est = MAPPER.readTree(estJson);
            String sessionToken = est.get("sessionToken").asText();
            log.info("  Session token: {}...", sessionToken.substring(0, Math.min(40, sessionToken.length())));
            log.info("  Expires in: {}s", est.get("expiresInSeconds").asInt());
            log.info("  Summary: {}", est.get("summary").asText());

            // Call protected tool with Bearer token
            log.info("========================================");
            log.info("  Calling protected MCP tool...");
            log.info("========================================");
            var dataClient = new HandshakeHttpClient("http://localhost:8080/mcp/data/tools");
            String result = dataClient.post("/get_secret_data", Map.of(), sessionToken);
            JsonNode data = MAPPER.readTree(result);
            log.info("  Result: {}", data.toPrettyString());

            log.info("========================================");
            log.info("  Handshake complete! ACCESS GRANTED");
            log.info("========================================");

        } catch (Exception e) {
            log.error("Handshake client failed", e);
        }
    }
}
