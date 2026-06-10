package io.jaiclaw.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tenant.DefaultTenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time chat sessions.
 * Clients connect to /ws/session/{sessionKey} and exchange JSON messages.
 *
 * <p>Inbound: {@code {"type": "message", "content": "hello"}}
 * <p>Outbound: {@code {"type": "response", "content": "Hi there!", "id": "..."}}
 *
 * <p><b>Tenant isolation.</b> The active-sessions map is keyed by
 * {@code {tenantId}:{sessionKey}} so two tenants connecting with the same
 * sessionKey never overwrite one another. The tenant is resolved at
 * handshake time from the connection's {@link Principal} (when available)
 * via {@link #resolveTenantFromPrincipal(Principal)}; absent a principal,
 * the handler falls back to the configured default tenantId (SINGLE mode)
 * or rejects the connection (MULTI mode).
 */
public class WebSocketSessionHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Attribute key under which the resolved tenant id is stashed on the session. */
    private static final String SESSION_TENANT_ATTR = "jaiclaw.tenantId";

    private final GatewayService gatewayService;
    private final TenantGuard tenantGuard;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WebSocketSessionHandler(GatewayService gatewayService, TenantGuard tenantGuard) {
        this.gatewayService = gatewayService;
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
    }

    /** Legacy ctor: defaults to a SINGLE-mode guard. Tests and unmigrated callers. */
    public WebSocketSessionHandler(GatewayService gatewayService) {
        this(gatewayService, new TenantGuard(TenantProperties.DEFAULT));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String tenantId = resolveTenantFromPrincipal(session.getPrincipal());
        if (tenantId == null && tenantGuard.isMultiTenant()) {
            log.warn("Rejecting WebSocket connection — MULTI mode requires a tenant but none was resolved from the principal: {}", session.getId());
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) {}
            return;
        }
        if (tenantId == null) {
            tenantId = tenantGuard.getProperties().defaultTenantId();
        }
        session.getAttributes().put(SESSION_TENANT_ATTR, tenantId);

        String sessionKey = extractSessionKey(session);
        sessions.put(storageKey(tenantId, sessionKey), session);
        log.info("WebSocket connected: tenant={}, sessionKey={}", tenantId, sessionKey);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        String sessionKey = extractSessionKey(session);
        String tenantId = (String) session.getAttributes().getOrDefault(
                SESSION_TENANT_ATTR, tenantGuard.getProperties().defaultTenantId());
        JsonNode json = MAPPER.readTree(textMessage.getPayload());

        String type = json.has("type") ? json.get("type").asText() : "message";
        String content = json.has("content") ? json.get("content").asText() : "";

        if (!"message".equals(type) || content.isBlank()) {
            sendJson(session, Map.of("type", "error", "message", "Invalid message format"));
            return;
        }

        // Set tenant context on this thread so gatewayService + any downstream code
        // Set tenant context on this thread so gatewayService and downstream
        // code see the originating tenant. The async continuations re-set the
        // captured tenantId before sending the response — see TenantContextPropagator
        // pattern.
        TenantContextHolder.set(new DefaultTenantContext(tenantId, tenantId));
        final String capturedTenantId = tenantId;
        try {
            gatewayService.handleAsync(sessionKey, content)
                    .thenAccept(response -> {
                        TenantContextHolder.set(new DefaultTenantContext(capturedTenantId, capturedTenantId));
                        try {
                            sendJson(session, Map.of(
                                    "type", "response",
                                    "id", response.id(),
                                    "content", response.content()));
                        } catch (IOException e) {
                            log.error("Failed to send WS response for session {}", sessionKey, e);
                        } finally {
                            TenantContextHolder.clear();
                        }
                    })
                    .exceptionally(ex -> {
                        TenantContextHolder.set(new DefaultTenantContext(capturedTenantId, capturedTenantId));
                        try {
                            sendJson(session, Map.of(
                                    "type", "error",
                                    "message", "Processing failed: " + ex.getMessage()));
                        } catch (IOException e) {
                            log.error("Failed to send WS error for session {}", sessionKey, e);
                        } finally {
                            TenantContextHolder.clear();
                        }
                        return null;
                    });
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionKey = extractSessionKey(session);
        String tenantId = (String) session.getAttributes().getOrDefault(
                SESSION_TENANT_ATTR, tenantGuard.getProperties().defaultTenantId());
        sessions.remove(storageKey(tenantId, sessionKey));
        log.info("WebSocket disconnected: tenant={}, sessionKey={} ({})", tenantId, sessionKey, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionKey = extractSessionKey(session);
        String tenantId = (String) session.getAttributes().getOrDefault(
                SESSION_TENANT_ATTR, tenantGuard.getProperties().defaultTenantId());
        log.warn("WebSocket transport error for {} (tenant {}): {}", sessionKey, tenantId, exception.getMessage());
        sessions.remove(storageKey(tenantId, sessionKey));
    }

    /**
     * Returns true if any session is open under any tenant for the given
     * raw sessionKey. Used by the gateway lifecycle for routing decisions.
     */
    public boolean hasActiveSession(String sessionKey) {
        String suffix = ":" + sessionKey;
        return sessions.entrySet().stream()
                .anyMatch(e -> e.getKey().endsWith(suffix) && e.getValue().isOpen());
    }

    /** True if the given (tenantId, sessionKey) tuple has an open WebSocket. */
    public boolean hasActiveSession(String tenantId, String sessionKey) {
        WebSocketSession session = sessions.get(storageKey(tenantId, sessionKey));
        return session != null && session.isOpen();
    }

    public int activeSessionCount() {
        return (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
    }

    /**
     * Build the storage key. Public for the {@code WebSocketSessionHandlerMultiTenantSpec}.
     */
    static String storageKey(String tenantId, String sessionKey) {
        return tenantId + ":" + sessionKey;
    }

    /**
     * Resolve the tenant from the connection's principal. Returns null when
     * no principal is attached. Override-points: a custom subclass can
     * pull the tenant from a JWT claim, a header captured at handshake, etc.
     */
    protected String resolveTenantFromPrincipal(Principal principal) {
        if (principal == null) return null;
        // The principal's name typically holds the tenant id when a JaiClaw
        // security filter has authenticated the upgrade request. Subclasses
        // can plug in a richer extractor.
        return principal.getName();
    }

    private String extractSessionKey(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        // Path pattern: /ws/session/{sessionKey}
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < path.length() - 1
                ? path.substring(lastSlash + 1)
                : "unknown";
    }

    private void sendJson(WebSocketSession session, Map<String, String> data) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(MAPPER.writeValueAsString(data)));
        }
    }
}
