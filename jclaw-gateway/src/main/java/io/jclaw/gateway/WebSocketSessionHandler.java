package io.jclaw.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for real-time chat sessions.
 * Clients connect to /ws/session/{sessionKey} and exchange JSON messages.
 *
 * <p>Inbound: {@code {"type": "message", "content": "hello"}}
 * <p>Outbound: {@code {"type": "response", "content": "Hi there!", "id": "..."}}
 */
public class WebSocketSessionHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GatewayService gatewayService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public WebSocketSessionHandler(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionKey = extractSessionKey(session);
        sessions.put(sessionKey, session);
        log.info("WebSocket connected: {}", sessionKey);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        String sessionKey = extractSessionKey(session);
        JsonNode json = MAPPER.readTree(textMessage.getPayload());

        String type = json.has("type") ? json.get("type").asText() : "message";
        String content = json.has("content") ? json.get("content").asText() : "";

        if (!"message".equals(type) || content.isBlank()) {
            sendJson(session, Map.of("type", "error", "message", "Invalid message format"));
            return;
        }

        gatewayService.handleAsync(sessionKey, content)
                .thenAccept(response -> {
                    try {
                        sendJson(session, Map.of(
                                "type", "response",
                                "id", response.id(),
                                "content", response.content()));
                    } catch (IOException e) {
                        log.error("Failed to send WS response for session {}", sessionKey, e);
                    }
                })
                .exceptionally(ex -> {
                    try {
                        sendJson(session, Map.of(
                                "type", "error",
                                "message", "Processing failed: " + ex.getMessage()));
                    } catch (IOException e) {
                        log.error("Failed to send WS error for session {}", sessionKey, e);
                    }
                    return null;
                });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionKey = extractSessionKey(session);
        sessions.remove(sessionKey);
        log.info("WebSocket disconnected: {} ({})", sessionKey, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String sessionKey = extractSessionKey(session);
        log.warn("WebSocket transport error for {}: {}", sessionKey, exception.getMessage());
        sessions.remove(sessionKey);
    }

    public boolean hasActiveSession(String sessionKey) {
        var session = sessions.get(sessionKey);
        return session != null && session.isOpen();
    }

    public int activeSessionCount() {
        return (int) sessions.values().stream().filter(WebSocketSession::isOpen).count();
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
