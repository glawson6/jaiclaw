package io.jaiclaw.channel.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * {@link TelegramHttpClient} implementation using {@code java.net.http.HttpClient}
 * (JDK 11+). Zero additional dependencies, virtual-thread friendly.
 *
 * <p>This is the default implementation. It avoids synchronized blocks that
 * cause virtual thread pinning (unlike {@code RestTemplate}'s default
 * {@code HttpURLConnection}).
 */
public class JdkHttpClientTelegramHttpClient implements TelegramHttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * @param pollingTimeoutSeconds Telegram long-poll timeout; the HTTP read timeout
     *                              is set to this value plus a 10-second buffer
     */
    public JdkHttpClientTelegramHttpClient(int pollingTimeoutSeconds) {
        this.requestTimeout = Duration.ofSeconds(pollingTimeoutSeconds + 10);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public JsonNode get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.sendAsync(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).join();
            return handleJsonResponse(response);
        } catch (CompletionException e) {
            throw unwrapCompletionException("GET " + redactUrl(url), e);
        } catch (IOException e) {
            throw new TelegramHttpException("GET " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public JsonNode post(String url, Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.sendAsync(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).join();
            return handleJsonResponse(response);
        } catch (CompletionException e) {
            throw unwrapCompletionException("POST " + redactUrl(url), e);
        } catch (IOException e) {
            throw new TelegramHttpException("POST " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public JsonNode postMultipart(String url, Map<String, Object> parts) {
        try {
            String boundary = UUID.randomUUID().toString();
            byte[] body = buildMultipartBody(parts, boundary);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = httpClient.sendAsync(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).join();
            return handleJsonResponse(response);
        } catch (CompletionException e) {
            throw unwrapCompletionException("POST multipart " + redactUrl(url), e);
        } catch (IOException e) {
            throw new TelegramHttpException("POST multipart " + redactUrl(url) + " failed", e);
        }
    }

    @Override
    public byte[] getBytes(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.sendAsync(request,
                    HttpResponse.BodyHandlers.ofByteArray()).join();
            if (response.statusCode() >= 400) {
                throw new TelegramHttpException(response.statusCode(),
                        "GET bytes " + redactUrl(url) + " returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (TelegramHttpException e) {
            throw e;
        } catch (CompletionException e) {
            throw unwrapCompletionException("GET bytes " + redactUrl(url), e);
        }
    }

    private JsonNode handleJsonResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            throw new TelegramHttpException(response.statusCode(),
                    "HTTP " + response.statusCode() + ": " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    /**
     * Build a multipart/form-data body from the parts map.
     * String values become text parts; byte[] values become file parts;
     * {@link MultipartFile} values carry both data and filename.
     */
    private byte[] buildMultipartBody(Map<String, Object> parts, String boundary) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        for (var entry : parts.entrySet()) {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            Object value = entry.getValue();
            if (value instanceof MultipartFile mp) {
                out.write(("Content-Disposition: form-data; name=\"" + entry.getKey()
                        + "\"; filename=\"" + mp.filename() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(mp.data());
            } else if (value instanceof byte[] bytes) {
                out.write(("Content-Disposition: form-data; name=\"" + entry.getKey()
                        + "\"; filename=\"file\"\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(bytes);
            } else {
                out.write(("Content-Disposition: form-data; name=\"" + entry.getKey()
                        + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            }
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * Unwrap a CompletionException from sendAsync().join() into a TelegramHttpException,
     * preserving interrupt status if the cause was an InterruptedException.
     */
    private static TelegramHttpException unwrapCompletionException(String context, CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new TelegramHttpException(context + " interrupted", cause);
        }
        if (cause instanceof IOException) {
            return new TelegramHttpException(context + " failed", cause);
        }
        return new TelegramHttpException(context + " failed", e);
    }

    /**
     * Redact the bot token from URLs for safe logging.
     */
    private static String redactUrl(String url) {
        return url.replaceAll("bot[^/]+/", "bot<redacted>/");
    }
}
