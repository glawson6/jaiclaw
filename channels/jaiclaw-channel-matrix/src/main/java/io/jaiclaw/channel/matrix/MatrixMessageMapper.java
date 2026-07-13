package io.jaiclaw.channel.matrix;

import tools.jackson.databind.JsonNode;
import io.jaiclaw.channel.ChannelMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts inbound {@link ChannelMessage} records from a Matrix sync response.
 *
 * <p>Parses the {@code rooms.join} section of the sync response, extracting
 * {@code m.room.message} timeline events with {@code m.text} message type.
 */
final class MatrixMessageMapper {

    private MatrixMessageMapper() {}

    /**
     * Extract all text messages from a Matrix sync response.
     *
     * @param syncResponse the full sync response JSON
     * @param botUserId    the bot's user ID to filter out own messages
     * @return list of inbound channel messages
     */
    static List<ChannelMessage> extractMessages(JsonNode syncResponse, String botUserId) {
        List<ChannelMessage> messages = new ArrayList<>();

        JsonNode rooms = syncResponse.path("rooms").path("join");
        if (rooms.isMissingNode()) return messages;

        var roomFields = rooms.fields();
        while (roomFields.hasNext()) {
            var roomEntry = roomFields.next();
            String roomId = roomEntry.getKey();
            JsonNode timeline = roomEntry.getValue().path("timeline").path("events");

            if (!timeline.isArray()) continue;

            for (JsonNode event : timeline) {
                ChannelMessage msg = mapEvent(event, roomId, botUserId);
                if (msg != null) {
                    messages.add(msg);
                }
            }
        }

        return messages;
    }

    /**
     * Convert a single Matrix timeline event to a {@link ChannelMessage}.
     *
     * @param event     the timeline event JSON
     * @param roomId    the room this event belongs to
     * @param botUserId the bot's user ID (to skip own messages)
     * @return the mapped message, or null if not a text message or sent by the bot
     */
    static ChannelMessage mapEvent(JsonNode event, String roomId, String botUserId) {
        String type = event.path("type").asText("");
        if (!"m.room.message".equals(type)) return null;

        String sender = event.path("sender").asText("");
        if (sender.isEmpty() || sender.equals(botUserId)) return null;

        JsonNode content = event.path("content");
        String msgtype = content.path("msgtype").asText("");
        if (!"m.text".equals(msgtype)) return null;

        String body = content.path("body").asText("");
        if (body.isEmpty()) return null;

        String eventId = event.path("event_id").asText("");

        Map<String, Object> platformData = Map.of(
                "roomId", roomId,
                "eventId", eventId,
                "msgtype", msgtype
        );

        return ChannelMessage.inbound(eventId, "matrix", roomId, sender, body, platformData);
    }
}
