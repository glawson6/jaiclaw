package io.jaiclaw.channel.matrix;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight Matrix Client-Server API client using {@link HttpClient}.
 *
 * <p>Implements the subset of the Matrix C-S API needed for a channel adapter:
 * <ul>
 *   <li>{@link #sync(String, int)} — long-poll sync for inbound events</li>
 *   <li>{@link #sendMessage(String, String)} — send a text message to a room</li>
 * </ul>
 */
public class MatrixApiClient {

    private static final Logger log = LoggerFactory.getLogger(MatrixApiClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String homeserverUrl;
    private final String accessToken;
    private final HttpClient httpClient;

    public MatrixApiClient(String homeserverUrl, String accessToken) {
        this(homeserverUrl, accessToken, HttpClient.newHttpClient());
    }

    public MatrixApiClient(String homeserverUrl, String accessToken, HttpClient httpClient) {
        this.homeserverUrl = homeserverUrl.endsWith("/")
                ? homeserverUrl.substring(0, homeserverUrl.length() - 1)
                : homeserverUrl;
        this.accessToken = accessToken;
        this.httpClient = httpClient;
    }

    /**
     * Long-poll sync for new events.
     *
     * @param sinceToken the sync token from the previous response (null for initial sync)
     * @param timeoutMs  server-side timeout in milliseconds
     * @return the sync response JSON
     */
    public JsonNode sync(String sinceToken, int timeoutMs) throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(homeserverUrl)
                .append("/_matrix/client/v3/sync?timeout=")
                .append(timeoutMs);

        if (sinceToken != null && !sinceToken.isEmpty()) {
            url.append("&since=").append(URLEncoder.encode(sinceToken, StandardCharsets.UTF_8));
        }

        // Only get message events to reduce payload size
        url.append("&filter=").append(URLEncoder.encode(
                "{\"room\":{\"timeline\":{\"types\":[\"m.room.message\"]},\"state\":{\"types\":[]}}}", StandardCharsets.UTF_8));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(url.toString()))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return MAPPER.readTree(response.body());
        } else {
            throw new IOException("Matrix sync failed: HTTP " + response.statusCode());
        }
    }

    /**
     * Send a text message to a Matrix room.
     *
     * @param roomId the room ID (e.g. "!abc123:matrix.org")
     * @param body   the message text
     * @return the event ID of the sent message
     */
    public String sendMessage(String roomId, String body) throws IOException, InterruptedException {
        String txnId = UUID.randomUUID().toString();
        String encodedRoomId = URLEncoder.encode(roomId, StandardCharsets.UTF_8);

        String json = MAPPER.writeValueAsString(Map.of(
                "msgtype", "m.text",
                "body", body
        ));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(homeserverUrl + "/_matrix/client/v3/rooms/"
                        + encodedRoomId + "/send/m.room.message/" + txnId))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode responseBody = MAPPER.readTree(response.body());
            return responseBody.path("event_id").asText(txnId);
        } else {
            throw new IOException("Matrix send failed: HTTP " + response.statusCode());
        }
    }
}
