package io.jaiclaw.discord.mcp;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.discord.config.DiscordToolsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * MCP tool provider exposing Discord-specific actions beyond basic messaging.
 * <p>
 * Provides tools for reactions, message editing/deletion, pins, threads,
 * polls, search, and channel history reading via the Discord REST API v10.
 * <p>
 * Requires the Discord channel adapter to be configured with a valid bot token.
 */
public class DiscordMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DiscordMcpToolProvider.class);
    private static final String SERVER_NAME = "discord";
    private static final String SERVER_DESCRIPTION = "Discord actions — reactions, pins, threads, polls, message management via Discord REST API";
    private static final String DISCORD_API = "https://discord.com/api/v10/";

    private final String botToken;
    private final DiscordToolsProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * Primary constructor — use {@link RestClient} (Spring 6.1+; the Boot 4 default).
     */
    public DiscordMcpToolProvider(String botToken,
                                  DiscordToolsProperties properties,
                                  RestClient restClient,
                                  ObjectMapper objectMapper) {
        this.botToken = botToken;
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Legacy constructor kept for backward compatibility with 0.9.x adopters that
     * still wire a {@link RestTemplate}. The RestTemplate reference is not used
     * (all requests go through the equivalent {@link RestClient} adapter);
     * migrate call-sites to the {@link RestClient} constructor.
     *
     * @deprecated Since 1.0.0. Migrate to {@link #DiscordMcpToolProvider(String,
     *     DiscordToolsProperties, RestClient, ObjectMapper)}.
     */
    @Deprecated(since = "1.0.0", forRemoval = true)
    public DiscordMcpToolProvider(String botToken,
                                  DiscordToolsProperties properties,
                                  RestTemplate restTemplate,
                                  ObjectMapper objectMapper) {
        this(botToken, properties, RestClient.create(), objectMapper);
    }

    @Override
    public String getServerName() { return SERVER_NAME; }

    @Override
    public String getServerDescription() { return SERVER_DESCRIPTION; }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition("discord_send", "Send a message to a Discord channel", SEND_SCHEMA),
                new McpToolDefinition("discord_read", "Read recent messages from a Discord channel", READ_SCHEMA),
                new McpToolDefinition("discord_react", "Add a reaction emoji to a Discord message", REACT_SCHEMA),
                new McpToolDefinition("discord_edit", "Edit a Discord message", EDIT_SCHEMA),
                new McpToolDefinition("discord_delete", "Delete a Discord message", DELETE_SCHEMA),
                new McpToolDefinition("discord_pin", "Pin a message in a Discord channel", PIN_SCHEMA),
                new McpToolDefinition("discord_unpin", "Unpin a message in a Discord channel", UNPIN_SCHEMA),
                new McpToolDefinition("discord_thread_create", "Create a thread from a Discord message", THREAD_CREATE_SCHEMA),
                new McpToolDefinition("discord_poll", "Create a poll in a Discord channel", POLL_SCHEMA)
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        if (tenant != null) {
            TenantContextHolder.set(tenant);
        }
        try {
            return switch (toolName) {
                case "discord_send" -> handleSend(args);
                case "discord_read" -> handleRead(args);
                case "discord_react" -> handleReact(args);
                case "discord_edit" -> handleEdit(args);
                case "discord_delete" -> handleDelete(args);
                case "discord_pin" -> handlePin(args);
                case "discord_unpin" -> handleUnpin(args);
                case "discord_thread_create" -> handleThreadCreate(args);
                case "discord_poll" -> handlePoll(args);
                default -> McpToolResult.error("Unknown tool: " + toolName);
            };
        } catch (IllegalArgumentException e) {
            return McpToolResult.error("Missing required parameter: " + e.getMessage());
        } catch (Exception e) {
            log.error("Discord tool execution failed: {}", toolName, e);
            return McpToolResult.error("Tool execution failed: " + e.getMessage());
        } finally {
            if (tenant != null) {
                TenantContextHolder.clear();
            }
        }
    }

    // ── Tool handlers ──

    private McpToolResult handleSend(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        String message = requireString(args, "message");
        boolean silent = Boolean.parseBoolean(stringOrDefault(args, "silent", "false"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", message);
        if (silent) {
            body.put("flags", 4096); // SUPPRESS_NOTIFICATIONS
        }

        JsonNode response = discordPost("channels/" + channelId + "/messages", body);
        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "messageId", response.path("id").asText(),
                "channelId", channelId)));
    }

    private McpToolResult handleRead(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        int limit = intOrDefault(args, "limit", 20);
        limit = Math.min(limit, 100);

        String url = "channels/" + channelId + "/messages?limit=" + limit;
        JsonNode response = discordGet(url);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (response.isArray()) {
            for (JsonNode msg : response) {
                messages.add(Map.of(
                        "id", msg.path("id").asText(),
                        "content", msg.path("content").asText(),
                        "author", msg.path("author").path("username").asText(),
                        "authorId", msg.path("author").path("id").asText(),
                        "timestamp", msg.path("timestamp").asText()
                ));
            }
        }
        return McpToolResult.success(toJson(Map.of("messages", messages, "count", messages.size())));
    }

    private McpToolResult handleReact(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");
        String emoji = requireString(args, "emoji");

        // URL-encode the emoji for the path
        String encodedEmoji = java.net.URLEncoder.encode(emoji, java.nio.charset.StandardCharsets.UTF_8);
        String url = "channels/" + channelId + "/messages/" + messageId + "/reactions/" + encodedEmoji + "/@me";
        discordPut(url);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId,
                "emoji", emoji)));
    }

    private McpToolResult handleEdit(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");
        String message = requireString(args, "message");

        Map<String, Object> body = Map.of("content", message);
        discordPatch("channels/" + channelId + "/messages/" + messageId, body);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId)));
    }

    private McpToolResult handleDelete(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");

        discordDelete("channels/" + channelId + "/messages/" + messageId);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId)));
    }

    private McpToolResult handlePin(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");

        discordPut("channels/" + channelId + "/pins/" + messageId);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId)));
    }

    private McpToolResult handleUnpin(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");

        discordDelete("channels/" + channelId + "/pins/" + messageId);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "channelId", channelId,
                "messageId", messageId)));
    }

    private McpToolResult handleThreadCreate(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        String messageId = requireString(args, "messageId");
        String threadName = requireString(args, "threadName");

        Map<String, Object> body = Map.of("name", threadName);
        JsonNode response = discordPost(
                "channels/" + channelId + "/messages/" + messageId + "/threads", body);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "threadId", response.path("id").asText(),
                "threadName", threadName)));
    }

    private McpToolResult handlePoll(Map<String, Object> args) throws JacksonException {
        String channelId = requireString(args, "channelId");
        String question = requireString(args, "question");

        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) args.get("options");
        if (options == null || options.size() < 2) {
            return McpToolResult.error("Poll requires at least 2 options");
        }

        boolean multiSelect = Boolean.parseBoolean(stringOrDefault(args, "multiSelect", "false"));
        int durationHours = intOrDefault(args, "durationHours", 24);

        // Build poll answers array
        List<Map<String, Object>> answers = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            answers.add(Map.of(
                    "answer_id", i + 1,
                    "poll_media", Map.of("text", options.get(i))
            ));
        }

        Map<String, Object> poll = new LinkedHashMap<>();
        poll.put("question", Map.of("text", question));
        poll.put("answers", answers);
        poll.put("duration", durationHours);
        poll.put("allow_multiselect", multiSelect);

        Map<String, Object> body = Map.of("poll", poll);
        JsonNode response = discordPost("channels/" + channelId + "/messages", body);

        return McpToolResult.success(toJson(Map.of(
                "success", true,
                "messageId", response.path("id").asText(),
                "channelId", channelId,
                "question", question)));
    }

    // ── Discord REST helpers ──

    private String authHeader() { return "Bot " + botToken; }

    private JsonNode discordPost(String path, Map<String, Object> body) {
        try {
            JsonNode result = restClient.post()
                    .uri(DISCORD_API + path)
                    .header("Authorization", authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return result != null ? result : objectMapper.createObjectNode();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Discord API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    private JsonNode discordGet(String path) {
        try {
            JsonNode result = restClient.get()
                    .uri(DISCORD_API + path)
                    .header("Authorization", authHeader())
                    .retrieve()
                    .body(JsonNode.class);
            return result != null ? result : objectMapper.createObjectNode();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Discord API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    private void discordPut(String path) {
        try {
            restClient.put()
                    .uri(DISCORD_API + path)
                    .header("Authorization", authHeader())
                    .header("Content-Length", "0")
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Discord API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    private void discordPatch(String path, Map<String, Object> body) {
        try {
            restClient.method(HttpMethod.PATCH)
                    .uri(DISCORD_API + path)
                    .header("Authorization", authHeader())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Discord API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
        }
    }

    private void discordDelete(String path) {
        try {
            restClient.delete()
                    .uri(DISCORD_API + path)
                    .header("Authorization", authHeader())
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Discord API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString());
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

    private String toJson(Object value) throws JacksonException {
        return objectMapper.writeValueAsString(value);
    }

    // ── JSON Schema constants ──

    private static final String SEND_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "message":{"type":"string","description":"Message text to send"},\
            "silent":{"type":"boolean","description":"Suppress notifications (default false)"}},\
            "required":["channelId","message"]}""";

    private static final String READ_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "limit":{"type":"integer","description":"Max messages to return (default 20, max 100)"}},\
            "required":["channelId"]}""";

    private static final String REACT_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "messageId":{"type":"string","description":"Discord message ID"},\
            "emoji":{"type":"string","description":"Emoji to react with (Unicode or :name: format)"}},\
            "required":["channelId","messageId","emoji"]}""";

    private static final String EDIT_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "messageId":{"type":"string","description":"Discord message ID"},\
            "message":{"type":"string","description":"New message content"}},\
            "required":["channelId","messageId","message"]}""";

    private static final String DELETE_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "messageId":{"type":"string","description":"Discord message ID"}},\
            "required":["channelId","messageId"]}""";

    private static final String PIN_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "messageId":{"type":"string","description":"Discord message ID to pin"}},\
            "required":["channelId","messageId"]}""";

    private static final String UNPIN_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "messageId":{"type":"string","description":"Discord message ID to unpin"}},\
            "required":["channelId","messageId"]}""";

    private static final String THREAD_CREATE_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "messageId":{"type":"string","description":"Discord message ID to create thread from"},\
            "threadName":{"type":"string","description":"Name for the new thread"}},\
            "required":["channelId","messageId","threadName"]}""";

    private static final String POLL_SCHEMA = """
            {"type":"object","properties":{\
            "channelId":{"type":"string","description":"Discord channel ID"},\
            "question":{"type":"string","description":"Poll question text"},\
            "options":{"type":"array","items":{"type":"string"},"description":"Poll answer choices (2-10)"},\
            "multiSelect":{"type":"boolean","description":"Allow multiple votes (default false)"},\
            "durationHours":{"type":"integer","description":"Poll duration in hours (default 24)"}},\
            "required":["channelId","question","options"]}""";
}
