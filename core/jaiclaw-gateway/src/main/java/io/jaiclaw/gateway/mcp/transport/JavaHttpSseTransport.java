package io.jaiclaw.gateway.mcp.transport;

import io.jaiclaw.core.http.ProxyAwareHttpClientFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;

/**
 * {@link SseHttpTransport} implementation backed by {@code java.net.http.HttpClient}.
 * Uses a dedicated cached thread pool executor to avoid ForkJoinPool deadlocks
 * when the SSE stream reader and HttpClient.send() compete for common pool threads.
 */
public class JavaHttpSseTransport implements SseHttpTransport {

    private final HttpClient httpClient;

    public JavaHttpSseTransport(String serverName) {
        this.httpClient = ProxyAwareHttpClientFactory.newBuilder()
                .executor(Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "mcp-http-" + serverName);
                    t.setDaemon(true);
                    return t;
                }))
                .build();
    }

    @Override
    public InputStream connectSseStream(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        return response.body();
    }

    @Override
    public int postJsonRpc(String url, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        return response.statusCode();
    }
}
