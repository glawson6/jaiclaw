package io.jclaw.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Manages the gateway lifecycle — starts all channel adapters on application startup
 * and stops them on shutdown. Implements SmartLifecycle so Spring manages ordering.
 */
public class GatewayLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GatewayLifecycle.class);

    private final GatewayService gatewayService;
    private volatile boolean running = false;

    public GatewayLifecycle(GatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Override
    public void start() {
        log.info("Starting JClaw gateway...");
        gatewayService.start();
        running = true;
    }

    @Override
    public void stop() {
        log.info("Stopping JClaw gateway...");
        gatewayService.stop();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Start after all beans are initialized, before web server accepts traffic
        return SmartLifecycle.DEFAULT_PHASE - 1;
    }
}
