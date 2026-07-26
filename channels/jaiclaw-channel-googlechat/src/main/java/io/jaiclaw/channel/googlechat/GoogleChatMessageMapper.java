package io.jaiclaw.channel.googlechat;

import tools.jackson.databind.JsonNode;
import io.jaiclaw.channel.ChannelMessage;

import java.util.Map;
import java.util.UUID;

/**
 * Maps Google Chat webhook event JSON to {@link ChannelMessage}.
 *
 * <p>Google Chat sends events via Pub/Sub push or HTTP endpoint. The event
 * payload includes {@code type}, {@code message}, {@code space}, and {@code user}.
 */
final class GoogleChatMessageMapper {

    private GoogleChatMessageMapper() {}

    /**
     * Convert a Google Chat event to an inbound {@link ChannelMessage}.
     *
     * @param event the JSON event payload from Google Chat
     * @return the mapped channel message, or null if the event is not a text message
     */
    static ChannelMessage mapEvent(JsonNode event) {
        String type = event.path("type").asText("");
        if (!"MESSAGE".equals(type)) return null;

        JsonNode message = event.path("message");
        String text = message.path("text").asText("");
        if (text.isEmpty()) return null;

        String messageId = message.path("name").asText(UUID.randomUUID().toString());
        String spaceName = event.path("space").path("name").asText("");
        String senderName = event.path("user").path("name").asText("");
        String senderDisplayName = event.path("user").path("displayName").asText("");

        if (senderName.isEmpty()) return null;

        Map<String, Object> platformData = Map.of(
                "spaceName", spaceName,
                "senderDisplayName", senderDisplayName,
                "threadName", message.path("thread").path("name").asText("")
        );

        return ChannelMessage.inbound(messageId, "googlechat", spaceName, senderName, text, platformData);
    }
}
