package io.jaiclaw.channel.telegram;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Abstraction over HTTP transport for Telegram Bot API calls.
 *
 * <p>Covers all HTTP operations used by {@link TelegramAdapter}:
 * <ul>
 *   <li>{@link #get} — getUpdates, getFile, deleteWebhook</li>
 *   <li>{@link #post} — sendMessage, setWebhook</li>
 *   <li>{@link #postMultipart} — sendDocument</li>
 *   <li>{@link #getBytes} — file binary download</li>
 * </ul>
 *
 * <p>Implementations must configure appropriate connect and read timeouts.
 * The read timeout should account for Telegram's long-poll timeout
 * (typically 30s) plus a buffer.
 */
public interface TelegramHttpClient {

    /**
     * Execute a GET request and parse the response as JSON.
     *
     * @param url fully-qualified Telegram Bot API URL
     * @return parsed JSON response body
     * @throws TelegramHttpException on HTTP errors or connectivity failures
     */
    JsonNode get(String url);

    /**
     * Execute a POST request with a JSON body and parse the response as JSON.
     *
     * @param url  fully-qualified Telegram Bot API URL
     * @param body request body (will be serialized to JSON)
     * @return parsed JSON response body
     * @throws TelegramHttpException on HTTP errors or connectivity failures
     */
    JsonNode post(String url, Object body);

    /**
     * Execute a POST request with multipart/form-data and parse the response as JSON.
     *
     * @param url   fully-qualified Telegram Bot API URL
     * @param parts map of part names to values (String for text parts, byte[] for binary)
     * @return parsed JSON response body
     * @throws TelegramHttpException on HTTP errors or connectivity failures
     */
    JsonNode postMultipart(String url, Map<String, Object> parts);

    /**
     * Execute a GET request and return the raw response bytes.
     *
     * @param url fully-qualified URL (typically a Telegram file download URL)
     * @return raw response body bytes
     * @throws TelegramHttpException on HTTP errors or connectivity failures
     */
    byte[] getBytes(String url);
}
