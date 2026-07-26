package io.jaiclaw.channel.line;

import tools.jackson.databind.JsonNode;
import io.jaiclaw.channel.ChannelMessage;

import java.util.Map;
import java.util.UUID;

/**
 * Maps LINE webhook event JSON to {@link ChannelMessage}.
 *
 * <p>LINE webhook events arrive as JSON with a top-level {@code events} array.
 * Each event contains {@code type}, {@code source}, {@code replyToken},
 * and type-specific fields (e.g. {@code message} for message events).
 */
final class LineMessageMapper {

    private LineMessageMapper() {}

    /**
     * Convert a LINE webhook message event to an inbound {@link ChannelMessage}.
     *
     * @param event the JSON event node from LINE's webhook callback
     * @return the mapped channel message, or null if the event is not a text message
     */
    static ChannelMessage mapEvent(JsonNode event) {
        String type = event.path("type").asText("");
        if (!"message".equals(type)) return null;

        JsonNode message = event.path("message");
        String messageType = message.path("type").asText("");
        if (!"text".equals(messageType)) return null;

        String text = message.path("text").asText("");
        if (text.isEmpty()) return null;

        String messageId = message.path("id").asText(UUID.randomUUID().toString());
        String userId = event.path("source").path("userId").asText("");
        String replyToken = event.path("replyToken").asText("");

        if (userId.isEmpty()) return null;

        Map<String, Object> platformData = Map.of(
                "replyToken", replyToken,
                "messageType", messageType,
                "sourceType", event.path("source").path("type").asText("")
        );

        return ChannelMessage.inbound(messageId, "line", "line-bot", userId, text, platformData);
    }
}
