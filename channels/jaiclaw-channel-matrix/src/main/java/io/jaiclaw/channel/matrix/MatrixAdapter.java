package io.jaiclaw.channel.matrix;

import tools.jackson.databind.JsonNode;
import io.jaiclaw.channel.AbstractChannelAdapter;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.DeliveryResult;
import io.jaiclaw.channel.chunking.PlatformLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Matrix messaging channel adapter using the Matrix Client-Server API.
 *
 * <p>Uses long-poll sync for inbound messages (similar to {@code SignalAdapter}
 * HTTP_CLIENT mode) and the {@link MatrixApiClient} for outbound messages.
 * No external SDK — pure {@link java.net.http.HttpClient} implementation.
 *
 * <p>Sync loop runs on a virtual thread, polling the homeserver for new events
 * and dispatching them to the inbound handler.
 *
 * <p>0.8.0 P3.3: now extends {@link AbstractChannelAdapter}.
 */
public class MatrixAdapter extends AbstractChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(MatrixAdapter.class);

    private final MatrixConfig config;
    private final MatrixApiClient apiClient;
    private final AtomicReference<String> sincToken = new AtomicReference<>();
    private Thread syncThread;

    public MatrixAdapter(MatrixConfig config, MatrixApiClient apiClient) {
        super("matrix", "Matrix", PlatformLimits.MATRIX);
        this.config = config;
        this.apiClient = apiClient;
    }

    @Override
    protected void doStart() {
        syncThread = Thread.ofVirtual().name("matrix-sync").start(() -> {
            log.info("Matrix sync loop started (homeserver={}, timeout={}ms)",
                    config.homeserverUrl(), config.syncTimeoutMs());

            while (isRunning() && !Thread.currentThread().isInterrupted()) {
                try {
                    pollSync();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Matrix sync error (will retry): {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Matrix sync loop stopped");
        });

        log.info("Matrix adapter started (user={})", config.userId());
    }

    @Override
    protected void doStop() {
        if (syncThread != null) {
            syncThread.interrupt();
        }
    }

    @Override
    protected DeliveryResult doSend(ChannelMessage message) {
        try {
            // roomId is stored in accountId (session key convention)
            String roomId = message.accountId();
            if (roomId == null || roomId.isEmpty()) {
                // Try platformData
                roomId = message.platformData() != null
                        ? (String) message.platformData().get("roomId")
                        : null;
            }

            if (roomId == null || roomId.isEmpty()) {
                return new DeliveryResult.Failure("no_room", "No room ID in message", false);
            }

            String eventId = apiClient.sendMessage(roomId, message.content());
            return new DeliveryResult.Success(eventId);
        } catch (Exception e) {
            log.error("Failed to send Matrix message: {}", e.getMessage(), e);
            return new DeliveryResult.Failure("send_failed", e.getMessage(), true);
        }
    }

    /**
     * Perform a single sync poll cycle. Visible for testing.
     */
    void pollSync() throws Exception {
        JsonNode syncResponse = apiClient.sync(sincToken.get(), config.syncTimeoutMs());

        // Update the since token for the next sync
        String nextBatch = syncResponse.path("next_batch").asText(null);
        if (nextBatch != null) {
            sincToken.set(nextBatch);
        }

        // Extract and dispatch messages
        var messages = MatrixMessageMapper.extractMessages(syncResponse, config.userId());
        for (ChannelMessage msg : messages) {
            String sender = msg.peerId();
            if (!config.isSenderAllowed(sender)) {
                log.debug("Dropping message from non-allowed Matrix sender {}", sender);
                continue;
            }
            dispatchInbound(msg);
        }
    }

    // Visible for testing
    String getSinceToken() {
        return sincToken.get();
    }
}
