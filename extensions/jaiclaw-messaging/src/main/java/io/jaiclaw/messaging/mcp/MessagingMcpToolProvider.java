package io.jaiclaw.messaging.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.channel.*;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.Message;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.gateway.GatewayService;
import io.jaiclaw.messaging.config.MessagingMcpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * MCP tool provider exposing messaging channel tools.
 * Server name: {@code messaging}, with 8 tools for channel messaging and agent-routed chat.
 */
public class MessagingMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(MessagingMcpToolProvider.class);
    private static final String SERVER_NAME = "messaging";
    private static final String SERVER_DESCRIPTION = "Channel messaging — send/receive messages, manage sessions, agent-routed chat across Telegram, Slack, Discord, SMS, Email, and more";

    private final ChannelRegistry channelRegistry;
    private final GatewayService gatewayService;
    private final SessionManager sessionManager;
    private final MessagingMcpProperties properties;
    private final ObjectMapper objectMapper;

    public MessagingMcpToolProvider(ChannelRegistry channelRegistry,
                                    GatewayService gatewayService,
                                    SessionManager sessionManager,
                                    MessagingMcpProperties properties,
                                    ObjectMapper objectMapper) {
        this.channelRegistry = channelRegistry;
        this.gatewayService = gatewayService;
        this.sessionManager = sessionManager;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("list_channels", "List registered messaging channels with running status", LIST_CHANNELS_SCHEMA),
                new McpToolDefinition("send_message", "Send a message through a specific channel adapter", SEND_MESSAGE_SCHEMA),
                new McpToolDefinition("get_channel_status", "Get the status of a channel adapter", GET_CHANNEL_STATUS_SCHEMA),
                new McpToolDefinition("list_sessions", "List conversation sessions", LIST_SESSIONS_SCHEMA),
                new McpToolDefinition("get_conversation", "Get message history for a session", GET_CONVERSATION_SCHEMA),
                new McpToolDefinition("broadcast_message", "Send a message to multiple recipients across channels", BROADCAST_MESSAGE_SCHEMA),
                new McpToolDefinition("agent_chat", "Route a message through the agent runtime and return the response synchronously", AGENT_CHAT_SCHEMA),
                new McpToolDefinition("agent_chat_async", "Route a message through the agent runtime and deliver the response to a channel asynchronously", AGENT_CHAT_ASYNC_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (tenant != null) {
            TenantContextHolder.set(tenant);
        }
        try {
            return switch (toolName) {
                case "list_channels" -> handleListChannels(args);
                case "send_message" -> handleSendMessage(args);
                case "get_channel_status" -> handleGetChannelStatus(args);
                case "list_sessions" -> handleListSessions(args);
                case "get_conversation" -> handleGetConversation(args);
                case "broadcast_message" -> handleBroadcastMessage(args);
                case "agent_chat" -> handleAgentChat(args);
                case "agent_chat_async" -> handleAgentChatAsync(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (IllegalArgumentException e) {
            return McpToolResult.error("Missing required parameter: " + e.getMessage());
        } catch (Exception e) {
            log.error("MCP tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        } finally {
            if (tenant != null) {
                TenantContextHolder.clear();
            }
        }
    }

    // ── Raw channel tools ──

    private McpToolResult handleListChannels(Map<String, Object> args) throws JsonProcessingException {
        List<Map<String, Object>> channels = channelRegistry.all().stream()
                .map(adapter -> Map.<String, Object>of(
                        "channelId", adapter.channelId(),
                        "displayName", adapter.displayName(),
                        "running", adapter.isRunning(),
                        "supportsStreaming", adapter.supportsStreaming()
                ))
                .toList();
        return McpToolResult.success(toJson(Map.of("channels", channels, "count", channels.size())));
    }

    private McpToolResult handleSendMessage(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");
        String peerId = requireString(args, "peerId");
        String content = requireString(args, "content");

        if (!properties.isChannelAllowed(channelId)) {
            return McpToolResult.error("Channel '" + channelId + "' is not in the allowed channels list");
        }

        Optional<ChannelAdapter> adapter = channelRegistry.get(channelId);
        if (adapter.isEmpty()) {
            return McpToolResult.error("Unknown channel: " + channelId);
        }

        ChannelMessage outbound = ChannelMessage.outbound(
                UUID.randomUUID().toString(), channelId, null, peerId, content);

        DeliveryResult result = adapter.get().sendMessage(outbound);
        return switch (result) {
            case DeliveryResult.Success s -> McpToolResult.success(toJson(Map.of(
                    "success", true,
                    "channelId", channelId,
                    "peerId", peerId,
                    "platformMessageId", s.platformMessageId())));
            case DeliveryResult.Failure f -> McpToolResult.error(
                    "Delivery failed: [" + f.errorCode() + "] " + f.message());
        };
    }

    private McpToolResult handleGetChannelStatus(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");

        Optional<ChannelAdapter> adapter = channelRegistry.get(channelId);
        if (adapter.isEmpty()) {
            return McpToolResult.error("Unknown channel: " + channelId);
        }

        ChannelAdapter a = adapter.get();
        return McpToolResult.success(toJson(Map.of(
                "channelId", a.channelId(),
                "displayName", a.displayName(),
                "running", a.isRunning(),
                "supportsStreaming", a.supportsStreaming()
        )));
    }

    private McpToolResult handleListSessions(Map<String, Object> args) throws JsonProcessingException {
        boolean activeOnly = Boolean.parseBoolean(stringOrDefault(args, "activeOnly", "false"));
        int limit = intOrDefault(args, "limit", 50);

        List<Session> sessions = activeOnly
                ? sessionManager.listActiveSessions()
                : sessionManager.listSessions();

        List<Map<String, Object>> result = sessions.stream()
                .limit(limit)
                .map(s -> Map.<String, Object>of(
                        "sessionKey", s.sessionKey(),
                        "agentId", s.agentId(),
                        "state", s.state().name(),
                        "messageCount", s.messages().size(),
                        "createdAt", s.createdAt().toString(),
                        "lastActiveAt", s.lastActiveAt().toString()
                ))
                .toList();

        return McpToolResult.success(toJson(Map.of("sessions", result, "count", result.size())));
    }

    private McpToolResult handleGetConversation(Map<String, Object> args) throws JsonProcessingException {
        String sessionKey = requireString(args, "sessionKey");
        int limit = intOrDefault(args, "limit", 100);

        Optional<Session> session = sessionManager.get(sessionKey);
        if (session.isEmpty()) {
            return McpToolResult.error("Session not found: " + sessionKey);
        }

        List<Message> messages = session.get().messages();
        List<Map<String, Object>> result = messages.stream()
                .skip(Math.max(0, messages.size() - limit))
                .map(m -> Map.<String, Object>of(
                        "role", m.getClass().getSimpleName().replace("Message", "").toLowerCase(),
                        "content", m.content(),
                        "timestamp", m.timestamp().toString()
                ))
                .toList();

        return McpToolResult.success(toJson(Map.of(
                "sessionKey", sessionKey,
                "messages", result,
                "count", result.size(),
                "totalMessages", messages.size()
        )));
    }

    private McpToolResult handleBroadcastMessage(Map<String, Object> args) throws JsonProcessingException {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recipients = (List<Map<String, Object>>) args.get("recipients");
        if (recipients == null || recipients.isEmpty()) {
            return McpToolResult.error("Missing required parameter: recipients");
        }
        String content = requireString(args, "content");

        if (recipients.size() > properties.maxRecipientsPerBroadcast()) {
            return McpToolResult.error("Too many recipients: " + recipients.size()
                    + " exceeds maximum of " + properties.maxRecipientsPerBroadcast());
        }

        int successCount = 0;
        int failureCount = 0;
        List<Map<String, Object>> failures = new ArrayList<>();

        for (Map<String, Object> recipient : recipients) {
            String channelId = (String) recipient.get("channelId");
            String peerId = (String) recipient.get("peerId");

            if (channelId == null || peerId == null) {
                failureCount++;
                failures.add(Map.of("channelId", String.valueOf(channelId),
                        "peerId", String.valueOf(peerId),
                        "error", "Missing channelId or peerId"));
                continue;
            }

            if (!properties.isChannelAllowed(channelId)) {
                failureCount++;
                failures.add(Map.of("channelId", channelId, "peerId", peerId,
                        "error", "Channel not allowed"));
                continue;
            }

            Optional<ChannelAdapter> adapter = channelRegistry.get(channelId);
            if (adapter.isEmpty()) {
                failureCount++;
                failures.add(Map.of("channelId", channelId, "peerId", peerId,
                        "error", "Unknown channel"));
                continue;
            }

            ChannelMessage outbound = ChannelMessage.outbound(
                    UUID.randomUUID().toString(), channelId, null, peerId, content);
            DeliveryResult result = adapter.get().sendMessage(outbound);

            if (result instanceof DeliveryResult.Success) {
                successCount++;
            } else if (result instanceof DeliveryResult.Failure f) {
                failureCount++;
                failures.add(Map.of("channelId", channelId, "peerId", peerId,
                        "error", f.message()));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalRecipients", recipients.size());
        response.put("successCount", successCount);
        response.put("failureCount", failureCount);
        if (!failures.isEmpty()) {
            response.put("failures", failures);
        }
        return McpToolResult.success(toJson(response));
    }

    // ── Agent-routed tools ──

    private McpToolResult handleAgentChat(Map<String, Object> args) throws JsonProcessingException {
        String content = requireString(args, "content");
        String channelId = stringOrDefault(args, "channelId", "mcp");
        String peerId = stringOrDefault(args, "peerId", "mcp-client");
        boolean deliverToChannel = Boolean.parseBoolean(stringOrDefault(args, "deliverToChannel", "false"));

        AssistantMessage response = gatewayService.handleSync(channelId, "mcp", peerId, content);

        if (deliverToChannel && channelRegistry.get(channelId).isPresent()) {
            ChannelMessage outbound = ChannelMessage.outbound(
                    UUID.randomUUID().toString(), channelId, null, peerId, response.content());
            channelRegistry.get(channelId).get().sendMessage(outbound);
        }

        return McpToolResult.success(toJson(Map.of(
                "response", response.content(),
                "modelId", response.modelId() != null ? response.modelId() : "",
                "timestamp", response.timestamp().toString()
        )));
    }

    private McpToolResult handleAgentChatAsync(Map<String, Object> args) throws JsonProcessingException {
        String content = requireString(args, "content");
        String channelId = requireString(args, "channelId");
        String peerId = requireString(args, "peerId");

        if (!properties.isChannelAllowed(channelId)) {
            return McpToolResult.error("Channel '" + channelId + "' is not in the allowed channels list");
        }

        String sessionKey = "default:" + channelId + ":mcp:" + peerId;

        CompletableFuture<AssistantMessage> future = gatewayService.handleAsync(sessionKey, content);
        future.thenAccept(response -> {
            channelRegistry.get(channelId).ifPresent(adapter -> {
                ChannelMessage outbound = ChannelMessage.outbound(
                        UUID.randomUUID().toString(), channelId, null, peerId, response.content());
                adapter.sendMessage(outbound);
            });
        }).exceptionally(ex -> {
            log.error("Async agent chat failed for session {}", sessionKey, ex);
            return null;
        });

        return McpToolResult.success(toJson(Map.of(
                "accepted", true,
                "sessionKey", sessionKey,
                "channelId", channelId,
                "peerId", peerId
        )));
    }

    // ── Helpers ──

    private String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key);
        }
        return value.toString();
    }

    private String stringOrDefault(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        return (value != null && !value.toString().isBlank()) ? value.toString() : defaultValue;
    }

    private int intOrDefault(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private String toJson(Object value) throws JsonProcessingException {
        return objectMapper.writeValueAsString(value);
    }

    // ── JSON Schema constants ──

    private static final String LIST_CHANNELS_SCHEMA = """
            {"type":"object","properties":{}}""";

    private static final String SEND_MESSAGE_SCHEMA = """
            {"type":"object","properties":{"channelId":{"type":"string","description":"Channel adapter ID (e.g. telegram, slack, sms)"},\
            "peerId":{"type":"string","description":"Recipient identifier on the channel"},\
            "content":{"type":"string","description":"Message text to send"}},"required":["channelId","peerId","content"]}""";

    private static final String GET_CHANNEL_STATUS_SCHEMA = """
            {"type":"object","properties":{"channelId":{"type":"string","description":"Channel adapter ID"}},"required":["channelId"]}""";

    private static final String LIST_SESSIONS_SCHEMA = """
            {"type":"object","properties":{"activeOnly":{"type":"boolean","description":"Only return active sessions"},\
            "limit":{"type":"integer","description":"Max sessions to return (default 50)"}}}""";

    private static final String GET_CONVERSATION_SCHEMA = """
            {"type":"object","properties":{"sessionKey":{"type":"string","description":"Session key to retrieve"},\
            "limit":{"type":"integer","description":"Max messages to return (most recent, default 100)"}},"required":["sessionKey"]}""";

    private static final String BROADCAST_MESSAGE_SCHEMA = """
            {"type":"object","properties":{"recipients":{"type":"array","items":{"type":"object","properties":\
            {"channelId":{"type":"string"},"peerId":{"type":"string"}},"required":["channelId","peerId"]},\
            "description":"List of recipients with channelId and peerId"},\
            "content":{"type":"string","description":"Message text to broadcast"}},"required":["recipients","content"]}""";

    private static final String AGENT_CHAT_SCHEMA = """
            {"type":"object","properties":{"content":{"type":"string","description":"Message to send to the agent"},\
            "channelId":{"type":"string","description":"Channel context (default: mcp)"},\
            "peerId":{"type":"string","description":"Peer identifier (default: mcp-client)"},\
            "deliverToChannel":{"type":"boolean","description":"Also deliver the response through the channel adapter"}},"required":["content"]}""";

    private static final String AGENT_CHAT_ASYNC_SCHEMA = """
            {"type":"object","properties":{"content":{"type":"string","description":"Message to send to the agent"},\
            "channelId":{"type":"string","description":"Channel to deliver agent response to"},\
            "peerId":{"type":"string","description":"Recipient on the channel"}},"required":["content","channelId","peerId"]}""";
}
