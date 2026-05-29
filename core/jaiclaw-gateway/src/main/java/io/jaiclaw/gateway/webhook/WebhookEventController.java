package io.jaiclaw.gateway.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller that receives incoming webhook events and routes them
 * to registered handlers via {@link WebhookRouteRegistry}.
 */
@RestController
public class WebhookEventController {

    private static final Logger log = LoggerFactory.getLogger(WebhookEventController.class);

    private final WebhookRouteRegistry registry;

    public WebhookEventController(WebhookRouteRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/webhooks/{*path}")
    public ResponseEntity<String> handleWebhook(
            @PathVariable String path,
            @RequestBody String body,
            @RequestHeader Map<String, String> headers) {

        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;

        var route = registry.findByPath(normalizedPath);
        if (route.isEmpty()) {
            log.warn("No webhook route registered for path: {}", normalizedPath);
            return ResponseEntity.notFound().build();
        }

        WebhookRoute webhookRoute = route.get();

        // Authenticate
        if (!WebhookAuthenticator.verify(webhookRoute, body, headers)) {
            log.warn("Webhook authentication failed for path: {}", normalizedPath);
            return ResponseEntity.status(401).body("Unauthorized");
        }

        // Dispatch to handler
        try {
            var event = new WebhookEvent(normalizedPath, Map.copyOf(headers), body, Instant.now());
            String response = webhookRoute.handler().apply(event);
            return ResponseEntity.ok(response != null ? response : "OK");
        } catch (Exception e) {
            log.error("Webhook handler failed for path: {}", normalizedPath, e);
            return ResponseEntity.internalServerError().body("Internal error");
        }
    }
}
