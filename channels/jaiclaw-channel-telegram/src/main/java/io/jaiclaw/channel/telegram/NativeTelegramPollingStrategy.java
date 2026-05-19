package io.jaiclaw.channel.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Native polling strategy using a virtual thread and the Telegram
 * {@code getUpdates} long-poll endpoint.
 *
 * <p>This is the fallback strategy for environments without Apache Camel.
 * It extracts the polling loop previously embedded in {@link TelegramAdapter}
 * with the following bug fixes:
 * <ul>
 *   <li>Catches {@code Throwable} (not just {@code Exception}) in the loop body
 *       to prevent silent thread death from errors like {@code OutOfMemoryError}</li>
 *   <li>Clears spurious interrupt flag after {@code httpClient.get()} returns,
 *       which can be set by JDK HttpClient's {@code sendAsync().join()} on
 *       virtual threads</li>
 *   <li>Adds diagnostic exit logging with running and interrupted state</li>
 * </ul>
 */
public class NativeTelegramPollingStrategy implements TelegramPollingStrategy {

    private static final Logger log = LoggerFactory.getLogger(NativeTelegramPollingStrategy.class);
    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final TelegramHttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong pollingOffset = new AtomicLong(0);
    private volatile Thread pollingThread;

    public NativeTelegramPollingStrategy(TelegramHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void startPolling(TelegramConfig config, TelegramUpdateHandler updateHandler) {
        if (!running.compareAndSet(false, true)) {
            log.warn("Native polling already started");
            return;
        }

        deleteWebhook(config);

        pollingThread = Thread.ofVirtual().name("telegram-poller").start(() -> {
            log.info("Telegram native polling started (timeout={}s)", config.pollingTimeoutSeconds());
            try {
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        pollUpdates(config, updateHandler);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Throwable e) {
                        log.warn("Telegram polling error (will retry): {}", e.getMessage());
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } finally {
                log.info("Telegram native polling stopped (running={}, interrupted={})",
                        running.get(), Thread.currentThread().isInterrupted());
            }
        });
    }

    @Override
    public void stopPolling() {
        running.set(false);
        Thread t = pollingThread;
        if (t != null) {
            t.interrupt();
        }
    }

    @Override
    public boolean isPolling() {
        return running.get();
    }

    private void pollUpdates(TelegramConfig config, TelegramUpdateHandler updateHandler)
            throws InterruptedException {
        String url = TELEGRAM_API_BASE + config.botToken() + "/getUpdates"
                + "?offset=" + pollingOffset.get()
                + "&timeout=" + config.pollingTimeoutSeconds()
                + "&allowed_updates=%5B%22message%22%5D";

        try {
            JsonNode response = httpClient.get(url);

            // Clear spurious interrupt flag that JDK HttpClient's
            // sendAsync().join() can leave on virtual threads
            Thread.interrupted();

            JsonNode result = response.path("result");
            if (!result.isArray()) return;

            log.debug("Telegram poll returned {} updates", result.size());

            for (JsonNode update : result) {
                long updateId = update.path("update_id").asLong();
                pollingOffset.set(updateId + 1);
                updateHandler.onUpdate(update);
            }
        } catch (TelegramHttpException e) {
            // Clear spurious interrupt flag even on error path
            Thread.interrupted();

            if (isTimeoutException(e)) {
                return; // Normal — no updates within timeout period
            }
            throw e;
        }
    }

    private void deleteWebhook(TelegramConfig config) {
        try {
            String url = TELEGRAM_API_BASE + config.botToken() + "/deleteWebhook";
            httpClient.get(url);
            log.debug("Deleted existing Telegram webhook (switching to polling)");
        } catch (Exception e) {
            log.warn("Failed to delete Telegram webhook: {}", e.getMessage());
        }
    }

    private static boolean isTimeoutException(TelegramHttpException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof java.net.SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
