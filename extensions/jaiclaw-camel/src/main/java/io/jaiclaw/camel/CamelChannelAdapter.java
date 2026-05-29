package io.jaiclaw.camel;

import io.jaiclaw.channel.ChannelAdapter;
import io.jaiclaw.channel.ChannelMessage;
import io.jaiclaw.channel.ChannelMessageHandler;
import io.jaiclaw.channel.ChannelRegistry;
import io.jaiclaw.channel.DeliveryResult;
import io.jaiclaw.channel.chunking.PlatformLimits;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Channel adapter that bridges Apache Camel routes with the JaiClaw gateway
 * using SEDA queues for async, multi-consumer routing.
 *
 * <p>Inbound: {@code seda:jaiclaw-{channelId}-in} — receives messages, converts to
 * {@link ChannelMessage}, and dispatches to the gateway handler.
 *
 * <p>Outbound: {@code seda:jaiclaw-{channelId}-out} — receives agent responses from
 * {@link #sendMessage(ChannelMessage)} and forwards them via configured bridge routes.
 */
public class CamelChannelAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(CamelChannelAdapter.class);

    static final String SEDA_IN_PREFIX = "seda:jaiclaw-";
    static final String SEDA_IN_SUFFIX = "-in";
    static final String SEDA_OUT_SUFFIX = "-out";

    static final String ROUTE_IN_PREFIX = "jaiclaw-in-";
    static final String ROUTE_IN_BRIDGE_PREFIX = "jaiclaw-in-bridge-";
    static final String ROUTE_OUT_BRIDGE_PREFIX = "jaiclaw-out-bridge-";
    static final String ROUTE_CH_BRIDGE_PREFIX = "jaiclaw-ch-bridge-";
    static final String ROUTE_LOG_PREFIX = "jaiclaw-log-";

    private static final List<String> ROUTE_PREFIXES = List.of(
            ROUTE_IN_PREFIX, ROUTE_IN_BRIDGE_PREFIX, ROUTE_OUT_BRIDGE_PREFIX,
            ROUTE_CH_BRIDGE_PREFIX, ROUTE_LOG_PREFIX
    );

    private final CamelChannelConfig config;
    private final ProducerTemplate producerTemplate;
    private final CamelContext camelContext;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ChannelMessageHandler handler;

    public CamelChannelAdapter(
            CamelChannelConfig config,
            ProducerTemplate producerTemplate,
            CamelContext camelContext) {
        this.config = config;
        this.producerTemplate = producerTemplate;
        this.camelContext = camelContext;
    }

    @Override
    public String channelId() {
        return config.channelId();
    }

    @Override
    public String displayName() {
        return config.displayName();
    }

    @Override
    public PlatformLimits platformLimits() {
        return config.platformLimits();
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;
        String routeId = ROUTE_IN_PREFIX + config.channelId();
        String fromUri = inboundEndpoint();

        try {
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(fromUri)
                            .routeId(routeId)
                            .process(exchange -> {
                                ChannelMessage message = CamelMessageConverter.toChannelMessage(
                                        exchange, config.channelId(), config.accountId());
                                CamelChannelAdapter.this.handler.onMessage(message);
                            });
                }
            });
            running.set(true);
            log.info("Started Camel channel adapter: {} (route: {})", config.channelId(), routeId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Camel channel adapter: " + config.channelId(), e);
        }
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            Exchange exchange = CamelMessageConverter.toExchange(message, camelContext);
            producerTemplate.send(outboundEndpoint(), exchange);
            return new DeliveryResult.Success(message.id());
        } catch (Exception e) {
            log.error("Failed to send message via Camel channel {}: {}", config.channelId(), e.getMessage(), e);
            return new DeliveryResult.Failure(
                    "CAMEL_SEND_ERROR",
                    e.getMessage(),
                    true
            );
        }
    }

    @Override
    public void stop() {
        for (String prefix : ROUTE_PREFIXES) {
            String routeId = prefix + config.channelId();
            stopAndRemoveRoute(routeId);
        }
        running.set(false);

        try {
            producerTemplate.stop();
        } catch (Exception e) {
            log.warn("Error stopping ProducerTemplate for channel {}: {}", config.channelId(), e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isStateless() {
        return config.stateless();
    }

    /**
     * Returns the inbound SEDA endpoint URI for this channel.
     * External Camel routes can send messages to this URI to trigger agent processing.
     */
    public String inboundEndpoint() {
        return SEDA_IN_PREFIX + config.channelId() + SEDA_IN_SUFFIX;
    }

    /**
     * Returns the outbound SEDA endpoint URI for this channel.
     * Bridge routes consume from this endpoint to forward agent responses.
     */
    public String outboundEndpoint() {
        return SEDA_IN_PREFIX + config.channelId() + SEDA_OUT_SUFFIX;
    }

    /**
     * Package-private config accessor for auto-configuration bridge creation.
     */
    CamelChannelConfig config() {
        return config;
    }

    /**
     * Creates an inbound bridge route: source URI → SEDA inbound queue.
     */
    void createInboundBridge(String inboundUri) {
        String routeId = ROUTE_IN_BRIDGE_PREFIX + config.channelId();
        String sedaIn = inboundEndpoint();
        try {
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(inboundUri)
                            .routeId(routeId)
                            .to(sedaIn);
                }
            });
            log.info("Created inbound bridge for channel {}: {} -> {}", config.channelId(), inboundUri, sedaIn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create inbound bridge for channel: " + config.channelId(), e);
        }
    }

    /**
     * Creates an outbound URI bridge route: SEDA outbound queue → Camel URI sink.
     */
    void createOutboundUriBridge(String outboundUri) {
        String routeId = ROUTE_OUT_BRIDGE_PREFIX + config.channelId();
        String sedaOut = outboundEndpoint();
        try {
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(sedaOut)
                            .routeId(routeId)
                            .to(outboundUri);
                }
            });
            log.info("Created outbound URI bridge for channel {}: {} -> {}", config.channelId(), sedaOut, outboundUri);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create outbound URI bridge for channel: " + config.channelId(), e);
        }
    }

    /**
     * Creates an outbound channel bridge route: SEDA outbound queue → another ChannelAdapter.
     */
    void createOutboundChannelBridge(ChannelRegistry channelRegistry, String targetChannelId) {
        String routeId = ROUTE_CH_BRIDGE_PREFIX + config.channelId();
        String sedaOut = outboundEndpoint();
        try {
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(sedaOut)
                            .routeId(routeId)
                            .process(exchange -> {
                                ChannelMessage outMsg = CamelMessageConverter.toChannelMessage(
                                        exchange, targetChannelId, config.accountId());
                                ChannelMessage outbound = ChannelMessage.outbound(
                                        outMsg.id(), targetChannelId, outMsg.accountId(),
                                        outMsg.peerId(), outMsg.content());
                                channelRegistry.get(targetChannelId).ifPresentOrElse(
                                        target -> target.sendMessage(outbound),
                                        () -> log.warn("Target channel '{}' not found in registry", targetChannelId)
                                );
                            });
                }
            });
            log.info("Created outbound channel bridge for channel {}: {} -> channel:{}",
                    config.channelId(), sedaOut, targetChannelId);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create outbound channel bridge for channel: " + config.channelId(), e);
        }
    }

    /**
     * Creates a logger fallback route: SEDA outbound queue → log output.
     */
    void createLoggerFallback() {
        String routeId = ROUTE_LOG_PREFIX + config.channelId();
        String sedaOut = outboundEndpoint();
        try {
            camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(sedaOut)
                            .routeId(routeId)
                            .log("JaiClaw [${header.JaiClawChannelId}] response: ${body}");
                }
            });
            log.warn("============================================================");
            log.warn("  No outbound configured for Camel channel '{}'", config.channelId());
            log.warn("  Agent responses will be logged only.");
            log.warn("  Configure outboundUri or outbound to route responses.");
            log.warn("============================================================");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create logger fallback for channel: " + config.channelId(), e);
        }
    }

    private void stopAndRemoveRoute(String routeId) {
        try {
            Route route = camelContext.getRoute(routeId);
            if (route != null) {
                camelContext.getRouteController().stopRoute(routeId);
                camelContext.removeRoute(routeId);
                log.debug("Stopped and removed route: {}", routeId);
            }
        } catch (Exception e) {
            log.error("Error stopping Camel route {}: {}", routeId, e.getMessage(), e);
        }
    }
}
