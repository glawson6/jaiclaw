package io.jaiclaw.slack.mcp;

import tools.jackson.core.JsonProcessingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.slack.config.SlackToolsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * MCP tool provider exposing Slack-specific actions beyond basic messaging.
 * <p>
 * Provides tools for reactions, message editing/deletion, pins, member info,
 * emoji list, and channel history reading via the Slack Web API.
 * <p>
 * Requires the Slack channel adapter to be configured with a valid bot token.
 */
public class SlackMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(SlackMcpToolProvider.class);
    private static final String SERVER_NAME = "slack";
    private static final String SERVER_DESCRIPTION = "Slack actions — reactions, pins, message management, member info via Slack Web API";
    private static final String SLACK_API = "https://slack.com/api/";

    private final String botToken;
    private final SlackToolsProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SlackMcpToolProvider(String botToken,
                                SlackToolsProperties properties,
                                RestTemplate restTemplate,
                                ObjectMapper objectMapper) {
        this.botToken = botToken;
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("slack_send", "Send a message to a Slack channel or user", SEND_SCHEMA),
                new McpToolDefinition("slack_read", "Read recent messages from a Slack channel", READ_SCHEMA),
                new McpToolDefinition("slack_react", "Add a reaction emoji to a Slack message", REACT_SCHEMA),
                new McpToolDefinition("slack_edit", "Edit a Slack message", EDIT_SCHEMA),
                new McpToolDefinition("slack_delete", "Delete a Slack message", DELETE_SCHEMA),
                new McpToolDefinition("slack_pin", "Pin a message in a Slack channel", PIN_SCHEMA),
                new McpToolDefinition("slack_unpin", "Unpin a message in a Slack channel", UNPIN_SCHEMA),
                new McpToolDefinition("slack_list_pins", "List pinned items in a Slack channel", LIST_PINS_SCHEMA),
                new McpToolDefinition("slack_member_info", "Get info about a Slack user", MEMBER_INFO_SCHEMA),
                new McpToolDefinition("slack_emoji_list", "List custom emoji in the Slack workspace", EMOJI_LIST_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (tenant != null) {
            TenantContextHolder.set(tenant);
        }
        try {
            return switch (toolName) {
                case "slack_send" -> handleSend(args);
                case "slack_read" -> handleRead(args);
                case "slack_react" -> handleReact(args);
                case "slack_edit" -> handleEdit(args);
                case "slack_delete" -> handleDelete(args);
                case "slack_pin" -> handlePin(args);
                case "slack_unpin" -> handleUnpin(args);
                case "slack_list_pins" -> handleListPins(args);
                case "slack_member_info" -> handleMemberInfo(args);
                case "slack_emoji_list" -> handleEmojiList(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (IllegalArgumentException e) {
            return McpToolResult.error("Missing required parameter: " + e.getMessage());
        } catch (Exception e) {
            log.error("Slack tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        } finally {
            if (tenant != null) {
                TenantContextHolder.clear();
            }
        }
    }

    // ── Tool handlers ──

    private McpToolResult handleSend(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");
        String content = requireString(args, "content");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channelId);
        body.put("text", content);

        JsonNode response = slackPost("chat.postMessage", body);
        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }
        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "ts", response.path("ts").asText(),
                "channelId", channelId)));
    }

    private McpToolResult handleRead(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");
        int limit = intOrDefault(args, "limit", 20);
        limit = Math.min(limit, 100);

        Map<String, Object> params = Map.of("channel", channelId, "limit", limit);
        JsonNode response = slackGet("conversations.history", params);

        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (JsonNode msg : response.path("messages")) {
            messages.add(Map.of(
                    "ts", msg.path("ts").asText(),
                    "text", msg.path("text").asText(),
                    "user", msg.path("user").asText("")
            ));
        }
        return McpToolResult.success(toJson(Map.of("messages", messages, "count", messages.size())));
    }

    private McpToolResult handleReact(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");
        String emoji = requireString(args, "emoji");

        // Strip colons if present (e.g. ":thumbsup:" -> "thumbsup")
        if (emoji.startsWith(":") && emoji.endsWith(":")) {
            emoji = emoji.substring(1, emoji.length() - 1);
        }

        Map<String, Object> body = Map.of(
                "channel", channelId,
                "timestamp", messageId,
                "name", emoji);
        JsonNode response = slackPost("reactions.add", body);

        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }
        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId,
                "emoji", emoji)));
    }

    private McpToolResult handleEdit(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");
        String content = requireString(args, "content");

        Map<String, Object> body = Map.of(
                "channel", channelId,
                "ts", messageId,
                "text", content);
        JsonNode response = slackPost("chat.update", body);

        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }
        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId)));
    }

    private McpToolResult handleDelete(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");

        Map<String, Object> body = Map.of(
                "channel", channelId,
                "ts", messageId);
        JsonNode response = slackPost("chat.delete", body);

        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }
        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId)));
    }

    private McpToolResult handlePin(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");

        Map<String, Object> body = Map.of(
                "channel", channelId,
                "timestamp", messageId);
        JsonNode response = slackPost("pins.add", body);

        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }
        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId)));
    }

    private McpToolResult handleUnpin(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");

        Map<String, Object> body = Map.of(
                "channel", channelId,
                "timestamp", messageId);
        JsonNode response = slackPost("pins.remove", body);

        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }
        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId)));
    }

    private McpToolResult handleListPins(Map<String, Object> args) throws JsonProcessingException {
        String channelId = requireString(args, "channelId");

        JsonNode response = slackGet("pins.list", Map.of("channel", channelId));
        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }

        List<Map<String, Object>> pins = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            JsonNode msg = item.path("message");
            pins.add(Map.of(
                    "ts", msg.path("ts").asText(),
                    "text", msg.path("text").asText(),
                    "user", msg.path("user").asText("")
            ));
        }
        return McpToolResult.success(toJson(Map.of("pins", pins, "count", pins.size())));
    }

    private McpToolResult handleMemberInfo(Map<String, Object> args) throws JsonProcessingException {
        String userId = requireString(args, "userId");

        JsonNode response = slackGet("users.info", Map.of("user", userId));
        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }

        JsonNode user = response.path("user");
        return McpToolResult.success(toJson(Map.of(
                "id", user.path("id").asText(),
                "name", user.path("name").asText(),
                "realName", user.path("real_name").asText(""),
                "displayName", user.path("profile").path("display_name").asText(""),
                "email", user.path("profile").path("email").asText(""),
                "isBot", user.path("is_bot").asBoolean(),
                "isAdmin", user.path("is_admin").asBoolean(),
                "timezone", user.path("tz").asText("")
        )));
    }

    private McpToolResult handleEmojiList(Map<String, Object> args) throws JsonProcessingException {
        JsonNode response = slackGet("emoji.list", Map.of());
        if (!response.path("ok").asBoolean()) {
            return McpToolResult.error("Slack API error: " + response.path("error").asText());
        }

        Map<String, String> emoji = new LinkedHashMap<>();
        response.path("emoji").fields().forEachRemaining(entry ->
                emoji.put(entry.getKey(), entry.getValue().asText()));
        return McpToolResult.success(toJson(Map.of("emoji", emoji, "count", emoji.size())));
    }

    // ── Slack REST helpers ──

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(botToken);
        return headers;
    }

    private JsonNode slackPost(String method, Map<String, Object> body) {
        var request = new HttpEntity<>(body, authHeaders());
        try {
            var response = restTemplate.postForEntity(SLACK_API + method, request, JsonNode.class);
            return response.getBody() != null ? response.getBody() : objectMapper.createObjectNode();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Slack API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    private JsonNode slackGet(String method, Map<String, Object> params) {
        StringBuilder url = new StringBuilder(SLACK_API + method);
        if (!params.isEmpty()) {
            url.append("?");
            params.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
            url.setLength(url.length() - 1); // remove trailing &
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(botToken);
        var request = new HttpEntity<>(headers);

        try {
            var response = restTemplate.exchange(
                    url.toString(), HttpMethod.GET, request, JsonNode.class);
            return response.getBody() != null ? response.getBody() : objectMapper.createObjectNode();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Slack API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    // ── Helpers ──

    private String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key);
        }
        return value.toString();
    }

    @SuppressWarnings("unused")
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

    private static final String SEND_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Slack channel or user ID (e.g. C123, U456)"},\
            "content":{"type":"string","description":"Message text to send"}},\
            "required":["channelId","content"]}""";

    private static final String READ_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Slack channel ID"},\
            "limit":{"type":"integer","description":"Max messages to return (default 20, max 100)"}},\
            "required":["channelId"]}""";

    private static final String REACT_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Slack channel ID"},\
            "messageId":{"type":"string","description":"Slack message timestamp (e.g. 1712023032.1234)"},\
            "emoji":{"type":"string","description":"Emoji name (e.g. thumbsup, :white_check_mark:, or Unicode)"}},\
            "required":["channelId","messageId","emoji"]}""";

    private static final String EDIT_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Slack channel ID"},\
            "messageId":{"type":"string","description":"Slack message timestamp"},\
            "content":{"type":"string","description":"New message content"}},\
            "required":["channelId","messageId","content"]}""";

    private static final String DELETE_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Slack channel ID"},\
            "messageId":{"type":"string","description":"Slack message timestamp"}},\
            "required":["channelId","messageId"]}""";

    private static final String PIN_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Slack channel ID"},\
            "messageId":{"type":"string","description":"Slack message timestamp to pin"}},\
            "required":["channelId","messageId"]}""";

    private static final String UNPIN_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Slack channel ID"},\
            "messageId":{"type":"string","description":"Slack message timestamp to unpin"}},\
            "required":["channelId","messageId"]}""";

    private static final String LIST_PINS_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Slack channel ID"}},\
            "required":["channelId"]}""";

    private static final String MEMBER_INFO_SCHEMA = """
            {"type":"object","properties":{\
            "userId":{"type":"string","description":"Slack user ID (e.g. U123)"}},\
            "required":["userId"]}""";

    private static final String EMOJI_LIST_SCHEMA = """
            {"type":"object","properties":{}}""";
}
