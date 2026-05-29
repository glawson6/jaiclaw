package io.jaiclaw.gateway.admin;

import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.model.SessionState;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin REST controller for operational management of the JaiClaw gateway.
 * Provides endpoints for session management, channel status, tool listing,
 * and runtime metrics. Conditional on {@code jaiclaw.admin.enabled=true}.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final SessionManager sessionManager;
    private final ChannelRegistry channelRegistry;
    private final ToolRegistry toolRegistry;

    public AdminController(SessionManager sessionManager,
                           ChannelRegistry channelRegistry,
                           ToolRegistry toolRegistry) {
        this.sessionManager = sessionManager;
        this.channelRegistry = channelRegistry;
        this.toolRegistry = toolRegistry;
    }

    // --- Session endpoints ---

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions() {
        List<Session> sessions = sessionManager.listSessions();
        var summaries = sessions.stream()
                .map(this::sessionSummary)
                .toList();
        return ResponseEntity.ok(Map.of("sessions", summaries, "count", summaries.size()));
    }

    @GetMapping("/sessions/{key}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String key) {
        return sessionManager.get(key)
                .map(session -> ResponseEntity.ok(sessionDetail(session)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/sessions/{key}")
    public ResponseEntity<Map<String, String>> terminateSession(@PathVariable String key) {
        Optional<Session> session = sessionManager.get(key);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessionManager.reset(key);
        log.info("Admin terminated session: {}", key);
        return ResponseEntity.ok(Map.of("status", "terminated", "sessionKey", key));
    }

    // --- Channel endpoints ---

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> listChannels() {
        var channels = channelRegistry.all().stream()
                .map(this::channelSummary)
                .toList();
        return ResponseEntity.ok(Map.of("channels", channels, "count", channels.size()));
    }

    @PostMapping("/channels/{channelId}/restart")
    public ResponseEntity<Map<String, String>> restartChannel(@PathVariable String channelId) {
        Optional<ChannelAdapter> adapter = channelRegistry.get(channelId);
        if (adapter.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ChannelAdapter channel = adapter.get();
        try {
            channel.stop();
            // Note: channel.start() requires a handler — typically the GatewayService.
            // For restart, we stop and let the lifecycle manager handle restart.
            log.info("Admin stopped channel: {}", channelId);
            return ResponseEntity.ok(Map.of("status", "stopped", "channelId", channelId,
                    "note", "Channel stopped. Lifecycle manager will handle restart."));
        } catch (Exception e) {
            log.error("Failed to restart channel: {}", channelId, e);
            return ResponseEntity.internalServerError().body(
                    Map.of("error", "Failed to restart channel: " + e.getMessage()));
        }
    }

    // --- Tool endpoints ---

    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> listTools() {
        var tools = toolRegistry.toolNames().stream()
                .map(name -> Map.of("name", (Object) name))
                .toList();
        return ResponseEntity.ok(Map.of("tools", tools, "count", tools.size()));
    }

    // --- Metrics summary ---

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        int activeSessions = sessionManager.listActiveSessions().size();
        int totalSessions = sessionManager.listSessions().size();
        int channelCount = channelRegistry.size();
        int toolCount = toolRegistry.toolNames().size();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("activeSessions", activeSessions);
        metrics.put("totalSessions", totalSessions);
        metrics.put("channels", channelCount);
        metrics.put("tools", toolCount);
        return ResponseEntity.ok(metrics);
    }

    // --- Helper methods ---

    private Map<String, Object> sessionSummary(Session session) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", session.id());
        map.put("sessionKey", session.sessionKey());
        map.put("state", session.state().name());
        map.put("agentId", session.agentId());
        map.put("messageCount", session.messages().size());
        if (session.tenantId() != null) map.put("tenantId", session.tenantId());
        return map;
    }

    private Map<String, Object> sessionDetail(Session session) {
        Map<String, Object> map = sessionSummary(session);
        map.put("messages", session.messages().size());
        return map;
    }

    private Map<String, Object> channelSummary(ChannelAdapter adapter) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("channelId", adapter.channelId());
        map.put("displayName", adapter.displayName());
        map.put("running", adapter.isRunning());
        map.put("stateless", adapter.isStateless());
        return map;
    }
}
