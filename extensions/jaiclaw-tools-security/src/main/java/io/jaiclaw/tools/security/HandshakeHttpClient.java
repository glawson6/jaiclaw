package io.jaiclaw.tools.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * HTTP client used by security tools in HTTP_CLIENT mode to communicate
 * with a remote MCP server's handshake endpoints.
 *
 * <p>The MCP server exposes handshake endpoints at:
 * <ul>
 *   <li>{@code POST {baseUrl}/capabilities} — advertise capabilities</li>
 *   <li>{@code POST {baseUrl}/negotiate} — key exchange</li>
 *   <li>{@code POST {baseUrl}/challenge} — issue challenge</li>
 *   <li>{@code POST {baseUrl}/verify} — verify identity</li>
 *   <li>{@code POST {baseUrl}/establish} — establish session</li>
 * </ul>
 */
public class HandshakeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(HandshakeHttpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient;

    public HandshakeHttpClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Send a handshake request to the remote MCP server.
     *
     * @param endpoint     the endpoint path (e.g. "/capabilities", "/negotiate")
     * @param payload      the JSON payload to send
     * @param bearerToken  optional bearer token for authenticated endpoints (null for unauthenticated)
     * @return the response body as a string
     */
    public String post(String endpoint, Map<String, Object> payload, String bearerToken) {
        try {
            String url = baseUrl + endpoint;
            String json = MAPPER.writeValueAsString(payload);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));

            if (bearerToken != null && !bearerToken.isBlank()) {
                builder.header("Authorization", "Bearer " + bearerToken);
            }

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Handshake HTTP request to " + url
                        + " failed with status " + response.statusCode()
                        + ": " + response.body());
            }

            log.debug("Handshake {} -> {} ({})", endpoint, response.statusCode(), response.body().length());
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Handshake HTTP request failed: " + endpoint, e);
        }
    }

    /**
     * Send a handshake request without authentication.
     */
    public String post(String endpoint, Map<String, Object> payload) {
        return post(endpoint, payload, null);
    }
}
