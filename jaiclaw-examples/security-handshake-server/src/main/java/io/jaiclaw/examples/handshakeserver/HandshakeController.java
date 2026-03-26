package io.jaiclaw.examples.handshakeserver;

import io.jaiclaw.tools.security.HandshakeServerEndpoint;
import io.jaiclaw.tools.security.HandshakeServerEndpoint.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller mapping the 5 handshake protocol endpoints to
 * {@link HandshakeServerEndpoint} typed methods.
 *
 * <p>Jackson deserializes request bodies directly into the inner records
 * ({@code NegotiateRequest}, etc.) — no manual map-to-record conversion.
 */
@RestController
@RequestMapping("/mcp/security")
public class HandshakeController {

    private static final Logger log = LoggerFactory.getLogger(HandshakeController.class);

    private final HandshakeServerEndpoint endpoint;

    public HandshakeController(HandshakeServerEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @PostMapping("/capabilities")
    public ResponseEntity<?> capabilities() {
        try {
            return ResponseEntity.ok(endpoint.capabilities());
        } catch (Exception e) {
            return handleError(e);
        }
    }

    @PostMapping("/negotiate")
    public ResponseEntity<?> negotiate(@RequestBody NegotiateRequest request) {
        try {
            return ResponseEntity.ok(endpoint.negotiate(request));
        } catch (Exception e) {
            return handleError(e);
        }
    }

    @PostMapping("/challenge")
    public ResponseEntity<?> challenge(@RequestBody ChallengeRequest request) {
        try {
            return ResponseEntity.ok(endpoint.challenge(request));
        } catch (Exception e) {
            return handleError(e);
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest request) {
        try {
            return ResponseEntity.ok(endpoint.verify(request));
        } catch (Exception e) {
            return handleError(e);
        }
    }

    @PostMapping("/establish")
    public ResponseEntity<?> establish(@RequestBody EstablishRequest request) {
        try {
            return ResponseEntity.ok(endpoint.establish(request));
        } catch (Exception e) {
            return handleError(e);
        }
    }

    private ResponseEntity<Map<String, String>> handleError(Exception e) {
        log.warn("Handshake error: {}", e.getMessage());
        Map<String, String> body = Map.of("error", e.getMessage());
        if (e instanceof SecurityException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            return ResponseEntity.badRequest().body(body);
        }
        return ResponseEntity.internalServerError().body(body);
    }
}
