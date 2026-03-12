package io.jclaw.gateway;

import io.jclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the gateway. Provides:
 * <ul>
 *   <li>POST /api/chat — synchronous message send/receive</li>
 *   <li>POST /webhook/{channelId} — inbound webhook from channel platforms</li>
 *   <li>GET /api/channels — list registered channels</li>
 *   <li>GET /api/health — gateway health check</li>
 * </ul>
 * <p>
 * Tenant context is resolved from request headers (JWT) and set on
 * {@link TenantContextHolder} before agent execution.
 */
@RestController
public class GatewayController {

    private static final Logger log = LoggerFactory.getLogger(GatewayController.class);

    private final GatewayService gatewayService;
    private final WebhookDispatcher webhookDispatcher;

    public GatewayController(GatewayService gatewayService, WebhookDispatcher webhookDispatcher) {
        this.gatewayService = gatewayService;
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * Synchronous chat endpoint — send a message and receive the agent's response.
     */
    @PostMapping("/api/chat")
    public ResponseEntity<ChatResponse> chat(
            @RequestBody ChatRequest request,
            @RequestHeader Map<String, String> headers) {

        // Resolve and set tenant context from request headers
        gatewayService.resolveTenant(headers).ifPresent(TenantContextHolder::set);

        try {
            var response = gatewayService.handleSync(
                    request.channelId() != null ? request.channelId() : "api",
                    request.accountId() != null ? request.accountId() : "default",
                    request.peerId() != null ? request.peerId() : "user",
                    request.content());

            return ResponseEntity.ok(new ChatResponse(response.id(), response.content()));
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * Webhook receiver — channel platforms POST inbound events here.
     * Each channel adapter registers a webhook handler via WebhookDispatcher.
     */
    @PostMapping("/webhook/{channelId}")
    public ResponseEntity<String> webhook(
            @PathVariable String channelId,
            @RequestBody String body,
            @RequestHeader Map<String, String> headers) {
        log.debug("Webhook received for channel: {}", channelId);
        return webhookDispatcher.dispatch(channelId, body, headers);
    }

    @GetMapping("/api/channels")
    public ResponseEntity<Map<String, Object>> channels() {
        return ResponseEntity.ok(Map.of("channels", webhookDispatcher.registeredChannels()));
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "component", "jclaw-gateway"));
    }

    public record ChatRequest(
            String content,
            String channelId,
            String accountId,
            String peerId
    ) {}

    public record ChatResponse(
            String id,
            String content
    ) {}
}
