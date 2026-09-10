package io.jaiclaw.learning.curator;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.learning.LearningProperties;
import io.jaiclaw.learning.skill.SkillWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs {@link SkillCurator} periodically across every tenant that has learned
 * skills.
 *
 * <p>Without this the curator exists but never fires: its transitions are driven
 * by elapsed time, so something has to ask it. A plain fixed-delay loop on a
 * daemon virtual thread rather than {@code @Scheduled} — this module must work in
 * a plain-Java embedding, not only under a Spring app with scheduling enabled.
 *
 * <p><strong>First run is deferred.</strong> Starting a curation pass during
 * application startup would add filesystem work to boot for no benefit, and on a
 * short-lived process (a CLI invocation, a test) it would never have been useful
 * anyway. The first pass waits one interval.
 *
 * <p>Tenants are discovered from the learned-skills directory rather than a
 * registry, so a tenant whose skills exist but which is not currently configured
 * still gets curated instead of accumulating forever.
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public class CuratorScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CuratorScheduler.class);

    /** How often to sweep. Ageing thresholds are in days; hourly is ample. */
    static final Duration DEFAULT_INTERVAL = Duration.ofHours(6);

    private final SkillCurator curator;
    private final SkillWriter skills;
    private final LearningProperties properties;
    private final Duration interval;
    private final Clock clock;

    private volatile Thread worker;
    private volatile boolean running;
    private volatile Instant lastRunAt;

    public CuratorScheduler(SkillCurator curator, SkillWriter skills, LearningProperties properties) {
        this(curator, skills, properties, DEFAULT_INTERVAL, Clock.systemUTC());
    }

    public CuratorScheduler(SkillCurator curator, SkillWriter skills,
                            LearningProperties properties, Duration interval, Clock clock) {
        this.curator = curator;
        this.skills = skills;
        this.properties = properties;
        this.interval = interval == null || interval.isZero() || interval.isNegative()
                ? DEFAULT_INTERVAL : interval;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** Starts the background sweep. No-op when the curator is disabled. */
    public synchronized void start() {
        if (running) return;
        if (!properties.curatorEnabled()) {
            log.debug("Skill curator is disabled; scheduler not started");
            return;
        }
        running = true;
        worker = Thread.ofVirtual().name("learning-curator").start(this::loop);
        log.info("Skill curator scheduled every {} (first pass deferred by one interval)", interval);
    }

    @Override
    public synchronized void close() {
        running = false;
        Thread t = worker;
        if (t != null) t.interrupt();
        worker = null;
    }

    private void loop() {
        while (running) {
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) return;
            try {
                runOnce();
            } catch (RuntimeException e) {
                // A failed sweep must never kill the scheduler; try again next interval.
                log.warn("Skill curation pass failed", e);
            }
        }
    }

    /**
     * Curates every tenant once. Exposed so an operator endpoint or a test can
     * trigger a sweep without waiting for the interval.
     *
     * @return the per-tenant reports
     */
    public List<SkillCurator.CuratorReport> runOnce() {
        List<SkillCurator.CuratorReport> reports = new ArrayList<>();
        for (String tenantId : discoverTenants()) {
            reports.add(curator.curate(tenantId));
        }
        lastRunAt = clock.instant();
        return List.copyOf(reports);
    }

    /** When the last sweep completed, or null if none has. */
    public Instant lastRunAt() {
        return lastRunAt;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Tenants that have a learned-skills directory. Derived from disk rather than
     * from configuration so a tenant that is no longer configured still gets its
     * skills aged rather than accumulating indefinitely.
     */
    List<String> discoverTenants() {
        Path root = Path.of(properties.skillsDir());
        if (!Files.isDirectory(root)) return List.of();
        List<String> tenants = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory)
                    .forEach(d -> tenants.add(d.getFileName().toString()));
        } catch (Exception e) {
            log.debug("Could not enumerate learned-skill tenants under {}", root, e);
            return List.of();
        }
        return List.copyOf(tenants);
    }
}
