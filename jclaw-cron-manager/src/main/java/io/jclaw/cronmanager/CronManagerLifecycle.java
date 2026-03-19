package io.jclaw.cronmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Lifecycle management for the Cron Job Manager.
 * Initializes the manager on startup (crash recovery + scheduler start)
 * and gracefully shuts down on application stop.
 */
@Component
public class CronManagerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(CronManagerLifecycle.class);

    private final CronJobManagerService managerService;
    private volatile boolean running = false;

    public CronManagerLifecycle(CronJobManagerService managerService) {
        this.managerService = managerService;
    }

    @Override
    public void start() {
        log.info("Starting Cron Job Manager lifecycle...");
        managerService.initialize();
        running = true;
    }

    @Override
    public void stop() {
        log.info("Stopping Cron Job Manager lifecycle...");
        managerService.shutdown();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Start after default Spring beans, stop before them
        return Integer.MAX_VALUE - 100;
    }
}
