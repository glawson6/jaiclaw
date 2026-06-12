package io.jaiclaw.kanban.recovery;

import io.jaiclaw.kanban.KanbanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically asks the {@link KanbanRecoveryManager} to apply
 * restart policies to RUNNING cards whose {@code startedAt} is older
 * than {@code jaiclaw.kanban.recovery.stale-running-timeout}. Handles
 * the hung-execution case where the JVM didn't crash but a processor
 * thread stalled.
 *
 * <p>{@link SmartLifecycle} so the scheduler can be shut down on
 * application stop alongside the rest of the runtime.
 */
public class StaleRunningDetector implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StaleRunningDetector.class);

    private final KanbanRecoveryManager manager;
    private final Duration staleTimeout;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "kanban-stale-detector");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = false;

    public StaleRunningDetector(KanbanRecoveryManager manager, KanbanProperties properties) {
        this.manager = manager;
        this.staleTimeout = parse(properties.recovery().staleRunningTimeout());
    }

    @Override
    public synchronized void start() {
        if (running) return;
        long every = Math.max(60, staleTimeout.getSeconds() / 4);
        scheduler.scheduleAtFixedRate(this::sweep, every, every, TimeUnit.SECONDS);
        running = true;
        log.info("Kanban stale-running detector started — timeout={} check-every={}s",
                staleTimeout, every);
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        scheduler.shutdownNow();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 200;
    }

    void sweep() {
        try {
            manager.sweepStale(staleTimeout);
        } catch (RuntimeException ex) {
            log.warn("Stale-running sweep failed: {}", ex.getMessage());
        }
    }

    static Duration parse(String value) {
        if (value == null || value.isBlank()) return Duration.ofMinutes(30);
        String trimmed = value.trim().toLowerCase();
        try {
            if (trimmed.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            }
            if (trimmed.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            }
            if (trimmed.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
            }
            return Duration.ofSeconds(Long.parseLong(trimmed));
        } catch (NumberFormatException ex) {
            log.warn("Invalid stale-running-timeout '{}' — falling back to 30m", value);
            return Duration.ofMinutes(30);
        }
    }
}
