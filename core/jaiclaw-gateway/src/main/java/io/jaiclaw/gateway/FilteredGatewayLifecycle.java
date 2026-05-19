package io.jaiclaw.gateway;

import io.jaiclaw.channel.ChannelMessageHandler;
import io.jaiclaw.channel.ChannelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway lifecycle that inserts a {@link ChannelMessageHandler} filter
 * between channel adapters and the {@link GatewayService}.
 *
 * <p>Extends {@link GatewayLifecycle} so that JaiClaw's auto-configuration's
 * {@code @ConditionalOnMissingBean} check will find this bean and skip creating
 * the default unfiltered lifecycle.
 *
 * <p>When channel adapters start, they receive the filter as their message
 * handler instead of the GatewayService directly. The filter then delegates
 * approved messages to the GatewayService.
 */
public class FilteredGatewayLifecycle extends GatewayLifecycle {

    private static final Logger log = LoggerFactory.getLogger(FilteredGatewayLifecycle.class);

    private final GatewayService gatewayService;
    private final ChannelRegistry channelRegistry;
    private final ChannelMessageHandler messageFilter;
    private volatile boolean running = false;

    /**
     * @param gatewayService  the core gateway service (downstream handler)
     * @param channelRegistry registry of all channel adapters
     * @param messageFilter   the filter to insert before the gateway service
     *                        (e.g., TelegramUserIdFilter)
     */
    public FilteredGatewayLifecycle(
            GatewayService gatewayService,
            ChannelRegistry channelRegistry,
            ChannelMessageHandler messageFilter) {
        super(gatewayService);
        this.gatewayService = gatewayService;
        this.channelRegistry = channelRegistry;
        this.messageFilter = messageFilter;
    }

    @Override
    public void start() {
        log.info("Starting filtered gateway...");

        // Start all channel adapters with the filter as their handler.
        // The filter's downstream must already be configured to point to gatewayService.
        channelRegistry.startAll(messageFilter);

        running = true;
        log.info("Filtered gateway started with {} channel adapters", channelRegistry.size());
    }

    @Override
    public void stop() {
        log.info("Stopping filtered gateway...");
        channelRegistry.stopAll();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return DEFAULT_PHASE - 1;
    }
}
