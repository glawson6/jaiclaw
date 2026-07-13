package io.jaiclaw.channel.telegram;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Polling strategy that delegates to Apache Camel's {@code camel-telegram}
 * consumer component.
 *
 * <p>Camel handles long-poll timeout, retry with backoff, thread management,
 * and error recovery internally. This strategy creates a Camel route that
 * consumes from the Telegram Bot API and delivers each update to the
 * {@link TelegramUpdateHandler}.
 *
 * <p>Requires {@code camel-telegram-starter} on the classpath.
 */
public class CamelTelegramPollingStrategy implements TelegramPollingStrategy {

    private static final Logger log = LoggerFactory.getLogger(CamelTelegramPollingStrategy.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ROUTE_ID = "jaiclaw-telegram-poller";

    private final CamelContext camelContext;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicLong syntheticUpdateId = new AtomicLong(System.currentTimeMillis());

    public CamelTelegramPollingStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public void startPolling(TelegramConfig config, TelegramUpdateHandler updateHandler) {
        if (!polling.compareAndSet(false, true)) {
            log.warn("Camel Telegram polling already started");
            return;
        }

        try {
            String telegramUri = "telegram:bots?authorizationToken="
                    + config.botToken()
                    + "&timeout=" + config.pollingTimeoutSeconds();

            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(telegramUri)
                            .routeId(ROUTE_ID)
                            .process(exchange -> {
                                Object body = exchange.getIn().getBody();
                                JsonNode update = wrapAsUpdate(body);
                                if (update != null) {
                                    updateHandler.onUpdate(update);
                                }
                            });
                }
            });

            log.info("Telegram Camel polling started (timeout={}s)", config.pollingTimeoutSeconds());
        } catch (Exception e) {
            polling.set(false);
            throw new IllegalStateException("Failed to start Camel Telegram polling", e);
        }
    }

    @Override
    public void stopPolling() {
        if (!polling.compareAndSet(true, false)) {
            return;
        }
        try {
            camelContext.getRouteController().stopRoute(ROUTE_ID);
            camelContext.removeRoute(ROUTE_ID);
            log.info("Telegram Camel polling stopped");
        } catch (Exception e) {
            log.warn("Error stopping Camel Telegram route: {}", e.getMessage());
        }
    }

    @Override
    public boolean isPolling() {
        return polling.get();
    }

    /**
     * Wrap a Camel exchange body into the Telegram Bot API update JSON structure
     * expected by {@link TelegramAdapter#processUpdate(JsonNode)}.
     *
     * <p>Camel's telegram component sets the Exchange body to an {@code IncomingMessage}
     * (not the full {@code Update} wrapper). This method reconstructs the expected
     * {@code {"update_id": N, "message": {...}}} envelope.
     */
    private JsonNode wrapAsUpdate(Object body) {
        try {
            JsonNode messageNode;
            if (body instanceof JsonNode node) {
                messageNode = node;
            } else if (body instanceof String str) {
                messageNode = MAPPER.readTree(str);
            } else if (body instanceof byte[] bytes) {
                messageNode = MAPPER.readTree(bytes);
            } else {
                // Camel model objects (IncomingMessage etc.) — serialize to JSON
                messageNode = MAPPER.valueToTree(body);
            }

            // If the node already has "message" and "update_id", it's already wrapped
            if (messageNode.has("message") && messageNode.has("update_id")) {
                return messageNode;
            }

            // Wrap as {"update_id": N, "message": {...}}
            ObjectNode wrapper = MAPPER.createObjectNode();
            wrapper.put("update_id", syntheticUpdateId.incrementAndGet());
            wrapper.set("message", messageNode);
            return wrapper;
        } catch (Exception e) {
            log.warn("Failed to convert Camel exchange body to JsonNode: {}", e.getMessage());
            return null;
        }
    }
}
