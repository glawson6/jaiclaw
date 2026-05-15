package io.jaiclaw.gateway.mcp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link SseHttpTransport} implementation backed by Spring {@code WebClient}.
 * Fully non-blocking internally — avoids the ForkJoinPool deadlock that
 * plagues the {@code java.net.http.HttpClient} approach.
 *
 * <p>Uses two separate {@code WebClient} instances with independent connection
 * pools and event loop groups. The SSE GET stream occupies one event loop
 * thread indefinitely; if the POST client shares the same event loop, its
 * requests can deadlock waiting for an event loop thread that is busy
 * servicing the SSE subscription. Separate loop groups prevent this.</p>
 */
public class WebClientSseTransport implements SseHttpTransport {

    private static final Logger log = LoggerFactory.getLogger(WebClientSseTransport.class);

    /** WebClient for the long-lived SSE GET stream. */
    private final WebClient sseClient;

    /** Separate WebClient with its own event loop for POST requests. */
    private final WebClient postClient;

    private final ConnectionProvider sseConnectionProvider;
    private final ConnectionProvider postConnectionProvider;
    private final LoopResources postLoopResources;

    public WebClientSseTransport() {
        // SSE stream: dedicated connection pool
        this.sseConnectionProvider = ConnectionProvider.builder("mcp-sse")
                .maxConnections(4)
                .build();
        HttpClient sseHttpClient = HttpClient.create(sseConnectionProvider);
        this.sseClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(sseHttpClient))
                .build();

        // POST requests: separate connection pool AND event loop group.
        // This is critical — the SSE subscription holds an event loop thread,
        // so POST requests must use a different event loop to avoid deadlock.
        this.postConnectionProvider = ConnectionProvider.builder("mcp-post")
                .maxConnections(8)
                .build();
        this.postLoopResources = LoopResources.create("mcp-post", 2, true);
        HttpClient postHttpClient = HttpClient.create(postConnectionProvider)
                .runOn(postLoopResources);
        this.postClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(postHttpClient))
                .build();
    }

    @Override
    public InputStream connectSseStream(String url) throws Exception {
        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream(pos, 16384);
        CountDownLatch firstData = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        log.debug("Connecting SSE stream to: {}", url);

        // Use exchangeToFlux to get the raw response body as DataBuffers,
        // bypassing any content-type-specific codecs (like the SSE codec).
        sseClient.get()
                .uri(url)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchangeToFlux(response -> {
                    log.debug("SSE connection established, status: {}", response.statusCode());
                    return response.bodyToFlux(DataBuffer.class);
                })
                .subscribe(
                        dataBuffer -> {
                            try {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                DataBufferUtils.release(dataBuffer);
                                log.debug("SSE received {} bytes", bytes.length);
                                pos.write(bytes);
                                pos.flush();
                                firstData.countDown();
                            } catch (IOException e) {
                                log.warn("Error writing SSE data to pipe", e);
                                try { pos.close(); } catch (IOException ignored) {}
                            }
                        },
                        error -> {
                            log.error("SSE stream error", error);
                            errorRef.set(error);
                            firstData.countDown();
                            try { pos.close(); } catch (IOException ignored) {}
                        },
                        () -> {
                            log.debug("SSE stream completed");
                            firstData.countDown();
                            try { pos.close(); } catch (IOException ignored) {}
                        }
                );

        // Wait for the first data chunk (the endpoint event) before returning.
        if (!firstData.await(10, TimeUnit.SECONDS)) {
            pos.close();
            throw new Exception("Timed out waiting for first SSE data from " + url);
        }

        if (errorRef.get() != null) {
            pos.close();
            throw new Exception("SSE connection error: " + errorRef.get().getMessage(), errorRef.get());
        }

        return pis;
    }

    @Override
    public int postJsonRpc(String url, String json) throws Exception {
        // Uses postClient (separate event loop group) and toFuture().get()
        // to avoid both event loop contention and Reactor's block() restriction.
        ResponseEntity<Void> response = postClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .retrieve()
                .toBodilessEntity()
                .toFuture()
                .get(30, TimeUnit.SECONDS);

        return response.getStatusCode().value();
    }

    @Override
    public void close() {
        sseConnectionProvider.dispose();
        postConnectionProvider.dispose();
        postLoopResources.dispose();
    }
}
