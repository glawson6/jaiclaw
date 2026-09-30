package io.jaiclaw.gateway;

import io.jaiclaw.channel.ChannelMessageHandler;
import io.jaiclaw.channel.ChannelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway lifecycle that inserts a chain of {@link GatewayMessageFilter}s
 * between channel adapters and the {@link GatewayService}.
 *
 * <p>Extends {@link GatewayLifecycle} so that the auto-configuration's
 * {@code @ConditionalOnMissingBean} check finds this bean and skips creating
 * the default unfiltered lifecycle.
 *
 * <p>When channel adapters start they receive the <em>head</em> of the filter
 * chain as their message handler. Each filter forwards to the next, and the
 * tail forwards to the GatewayService.
 *
 * <p><strong>This class owns chain construction.</strong> Filters are wired in
 * {@link #start()} rather than at bean-creation time, so a filter needs no
 * reference to the GatewayService and cannot be handed the wrong downstream.
 * Filters are applied in the order supplied — the caller is responsible for
 * sorting (the auto-configuration uses {@code ObjectProvider.orderedStream()},
 * which honours {@code @Order}).
 */
public class FilteredGatewayLifecycle extends GatewayLifecycle {

    private static final Logger log = LoggerFactory.getLogger(FilteredGatewayLifecycle.class);

    private final GatewayService gatewayService;
    private final ChannelRegistry channelRegistry;
    private final List<GatewayMessageFilter> filters;
    private volatile boolean running = false;

    /**
     * @param gatewayService  the core gateway service (chain tail)
     * @param channelRegistry registry of all channel adapters
     * @param filters         filters to insert, in the order they should run;
     *                        an empty list behaves like an unfiltered gateway
     */
    public FilteredGatewayLifecycle(
            GatewayService gatewayService,
            ChannelRegistry channelRegistry,
            List<GatewayMessageFilter> filters) {
        super(gatewayService);
        this.gatewayService = gatewayService;
        this.channelRegistry = channelRegistry;
        this.filters = filters == null ? List.of() : List.copyOf(filters);
    }

    /**
     * Single-filter convenience constructor, kept so existing callers compile
     * unchanged.
     */
    public FilteredGatewayLifecycle(
            GatewayService gatewayService,
            ChannelRegistry channelRegistry,
            GatewayMessageFilter messageFilter) {
        this(gatewayService, channelRegistry,
                messageFilter == null ? List.of() : List.of(messageFilter));
    }

    @Override
    public void start() {
        log.info("Starting filtered gateway...");

        ChannelMessageHandler head = buildChain();

        // Start all channel adapters with the chain head as their handler.
        channelRegistry.startAll(head);

        running = true;
        log.info("Filtered gateway started with {} channel adapters and {} filter(s): {}",
                channelRegistry.size(), filters.size(), filterNames());
    }

    /**
     * Links each filter to the next and the last to the GatewayService,
     * returning the handler adapters should publish into.
     *
     * <p>Built back-to-front so every {@code setDownstream} receives an
     * already-linked handler — a filter is never briefly pointing at nothing.
     */
    private ChannelMessageHandler buildChain() {
        ChannelMessageHandler next = gatewayService;
        for (int i = filters.size() - 1; i >= 0; i--) {
            GatewayMessageFilter filter = filters.get(i);
            filter.setDownstream(next);
            next = filter;
        }
        return next;
    }

    private List<String> filterNames() {
        List<String> names = new ArrayList<>(filters.size());
        for (GatewayMessageFilter f : filters) {
            names.add(f.getClass().getSimpleName());
        }
        return names;
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
