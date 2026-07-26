package io.jaiclaw.session.redis;

import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.MediaAttachment;
import io.jaiclaw.core.model.Message;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.model.SessionState;
import io.jaiclaw.core.model.SystemMessage;
import io.jaiclaw.core.model.TokenUsage;
import io.jaiclaw.core.model.ToolResultMessage;
import io.jaiclaw.core.model.UserMessage;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled envelope codec for {@link Session} + {@link Message}. Uses
 * an explicit {@code "type"} discriminator on each Message so the sealed
 * hierarchy round-trips through JSON without touching core
 * (i.e. without adding {@code @JsonTypeInfo} annotations to the
 * {@code io.jaiclaw.core.model} tree).
 *
 * <p>Package-private — only {@link RedisSessionManager} calls into this.
 *
 * <p>Envelope shape for messages:
 *
 * <pre>{@code
 * { "type": "user" | "assistant" | "system" | "tool_result",
 *   "id": "...",
 *   "timestamp": "2026-07-20T...Z",
 *   "content": "...",
 *   "metadata": { ... },
 *   // subtype-specific:
 *   "senderId": "...",           // user
 *   "media": [ ... ],            // user (base64 bytes)
 *   "modelId": "...",            // assistant
 *   "usage": { ... },            // assistant
 *   "toolCallId": "...",         // tool_result
 *   "toolName": "..."            // tool_result
 * }
 * }</pre>
 *
 * <p>Session envelope carries every field except {@code messages} — the
 * message list is stored under a separate Redis LIST key. An advisory
 * {@code messageCount} is written alongside for cheap "is-there-anything"
 * checks, but the source of truth is {@code LLEN} on the message list.
 *
 * <p>Forward-compat: unknown envelope fields are ignored on decode;
 * missing optional fields decode to sensible defaults (empty
 * collections, {@code null} scalars). A future Message-subtype field
 * roll-out won't corrupt older Redis payloads.
 */
final class SessionCodec {

    private final ObjectMapper json;

    SessionCodec() {
        this.json = new ObjectMapper();
    }

    // ── Session envelope ─────────────────────────────

    String encodeSession(Session session) {
        ObjectNode node = json.createObjectNode();
        node.put("id", session.id());
        node.put("sessionKey", session.sessionKey());
        node.put("agentId", session.agentId());
        node.put("tenantId", session.tenantId());
        node.put("createdAt", session.createdAt() == null ? null : session.createdAt().toString());
        node.put("lastActiveAt", session.lastActiveAt() == null ? null : session.lastActiveAt().toString());
        node.put("state", session.state() == null ? null : session.state().name());
        node.put("messageCount", session.messages() == null ? 0 : session.messages().size());
        try {
            return json.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to encode Session " + session.sessionKey(), e);
        }
    }

    Session decodeSession(String jsonBody, List<Message> messages) {
        JsonNode node;
        try {
            node = json.readTree(jsonBody);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to decode Session JSON", e);
        }
        String id = textOrNull(node, "id");
        String sessionKey = textOrNull(node, "sessionKey");
        String agentId = textOrNull(node, "agentId");
        String tenantId = textOrNull(node, "tenantId");
        Instant createdAt = instantOrNull(node, "createdAt");
        Instant lastActiveAt = instantOrNull(node, "lastActiveAt");
        SessionState state = stateOrDefault(node);
        List<Message> safeMessages = messages == null ? List.of() : messages;
        return new Session(id, sessionKey, agentId, tenantId,
                createdAt, lastActiveAt, state, safeMessages);
    }

    // ── Message envelope ─────────────────────────────

    String encodeMessage(Message message) {
        ObjectNode node = json.createObjectNode();
        node.put("id", message.id());
        node.put("timestamp", message.timestamp() == null ? null : message.timestamp().toString());
        node.put("content", message.content());
        if (message.metadata() != null && !message.metadata().isEmpty()) {
            node.set("metadata", json.valueToTree(message.metadata()));
        }
        switch (message) {
            case UserMessage u -> {
                node.put("type", "user");
                node.put("senderId", u.senderId());
                if (u.media() != null && !u.media().isEmpty()) {
                    ArrayNode media = json.createArrayNode();
                    for (MediaAttachment attachment : u.media()) {
                        ObjectNode a = json.createObjectNode();
                        a.put("mimeType", attachment.mimeType());
                        a.put("bytes", attachment.bytes());   // base64 by Jackson
                        a.put("filename", attachment.filename());
                        media.add(a);
                    }
                    node.set("media", media);
                }
            }
            case AssistantMessage a -> {
                node.put("type", "assistant");
                node.put("modelId", a.modelId());
                if (a.usage() != null) {
                    ObjectNode usage = json.createObjectNode();
                    usage.put("inputTokens", a.usage().inputTokens());
                    usage.put("outputTokens", a.usage().outputTokens());
                    usage.put("cacheReadTokens", a.usage().cacheReadTokens());
                    usage.put("cacheWriteTokens", a.usage().cacheWriteTokens());
                    node.set("usage", usage);
                }
            }
            case SystemMessage s -> {
                node.put("type", "system");
                // No extra fields.
                // Suppress unused-variable warning:
                if (s == null) throw new IllegalStateException("unreachable");
            }
            case ToolResultMessage t -> {
                node.put("type", "tool_result");
                node.put("toolCallId", t.toolCallId());
                node.put("toolName", t.toolName());
            }
        }
        try {
            return json.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to encode Message " + message.id(), e);
        }
    }

    Message decodeMessage(String jsonBody) {
        JsonNode node;
        try {
            node = json.readTree(jsonBody);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to decode Message JSON", e);
        }
        String type = textOrNull(node, "type");
        if (type == null) {
            throw new IllegalStateException("Message envelope missing 'type' field");
        }
        String id = textOrNull(node, "id");
        Instant timestamp = instantOrNull(node, "timestamp");
        String content = textOrNull(node, "content");
        Map<String, Object> metadata = readMetadata(node.get("metadata"));

        return switch (type) {
            case "user" -> new UserMessage(
                    id, timestamp, content,
                    textOrNull(node, "senderId"),
                    metadata,
                    readMedia(node.get("media")));
            case "assistant" -> new AssistantMessage(
                    id, timestamp, content,
                    textOrNull(node, "modelId"),
                    readUsage(node.get("usage")),
                    metadata);
            case "system" -> new SystemMessage(id, timestamp, content, metadata);
            case "tool_result" -> new ToolResultMessage(
                    id, timestamp, content,
                    textOrNull(node, "toolCallId"),
                    textOrNull(node, "toolName"),
                    metadata);
            default -> throw new IllegalStateException("Unknown message type: " + type);
        };
    }

    // ── helpers ──────────────────────────────────────

    private static String textOrNull(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) return null;
        return n.asString();
    }

    private static Instant instantOrNull(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null) return null;
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static SessionState stateOrDefault(JsonNode node) {
        String value = textOrNull(node, "state");
        if (value == null) return SessionState.ACTIVE;
        try {
            return SessionState.valueOf(value);
        } catch (IllegalArgumentException e) {
            return SessionState.ACTIVE;
        }
    }

    private Map<String, Object> readMetadata(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.properties().iterator();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            out.put(entry.getKey(), json.convertValue(entry.getValue(), Object.class));
        }
        return out;
    }

    private List<MediaAttachment> readMedia(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) return List.of();
        List<MediaAttachment> out = new ArrayList<>();
        for (JsonNode item : node) {
            String mimeType = textOrNull(item, "mimeType");
            String filename = textOrNull(item, "filename");
            byte[] bytes = null;
            JsonNode b = item.get("bytes");
            if (b != null && !b.isNull()) {
                try {
                    bytes = b.binaryValue();
                } catch (Exception e) {
                    // Skip malformed attachment.
                    continue;
                }
            }
            if (mimeType == null || bytes == null) continue;
            out.add(new MediaAttachment(mimeType, bytes, filename == null ? "" : filename));
        }
        return out;
    }

    private static TokenUsage readUsage(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) return null;
        return new TokenUsage(
                intOrZero(node, "inputTokens"),
                intOrZero(node, "outputTokens"),
                intOrZero(node, "cacheReadTokens"),
                intOrZero(node, "cacheWriteTokens"));
    }

    private static int intOrZero(JsonNode node, String field) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull() || !n.isNumber()) return 0;
        return n.asInt();
    }
}
