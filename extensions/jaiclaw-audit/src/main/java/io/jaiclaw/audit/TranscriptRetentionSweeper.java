package io.jaiclaw.audit;

import io.jaiclaw.core.api.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Deletes archived transcripts older than a retention window.
 *
 * <p>{@link FileTranscriptStore} writes to {@code {store}/{tenant}/{date}/{id}.json}
 * and has always offered {@code delete(sessionId)} — but nothing ever called it on
 * a schedule, so an adopter who enabled transcript archiving accumulated
 * conversation text on disk indefinitely. For a store holding user conversations
 * that is a data-protection problem, not a disk-space one: "we keep it forever
 * because nobody wrote the cleanup" is not a retention policy anyone would choose.
 *
 * <p>Sweeping is <strong>off by default</strong> ({@link #disabled()}). Deleting
 * an adopter's audit data because they upgraded would be far worse than the leak,
 * so retention must be set deliberately.
 *
 * <p>The date partition is what makes this cheap and safe: whole day-directories
 * are compared and removed, so the sweeper never parses a transcript, never
 * decides based on file contents, and cannot delete today's data through a
 * rounding error. A directory whose name is not an ISO date is left alone.
 *
 * <p>Phase 1.2.0 follow-up — transcript retention.
 */
@Experimental
public class TranscriptRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(TranscriptRetentionSweeper.class);

    /** Sweep interval. Retention is in days; hourly is far more often than needed. */
    static final Duration DEFAULT_INTERVAL = Duration.ofHours(12);

    private final Path storeDir;
    private final Duration retention;
    private final Duration interval;
    private final Clock clock;

    private volatile Thread worker;
    private volatile boolean running;

    /**
     * @param storeDir  the transcript store root
     * @param retention how long to keep transcripts; null or non-positive disables sweeping
     */
    public TranscriptRetentionSweeper(Path storeDir, Duration retention) {
        this(storeDir, retention, DEFAULT_INTERVAL, Clock.systemUTC());
    }

    public TranscriptRetentionSweeper(Path storeDir, Duration retention,
                                      Duration interval, Clock clock) {
        this.storeDir = storeDir;
        this.retention = retention != null && !retention.isZero() && !retention.isNegative()
                ? retention : null;
        this.interval = interval == null || interval.isZero() || interval.isNegative()
                ? DEFAULT_INTERVAL : interval;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** A sweeper that never deletes anything — the default. */
    public static TranscriptRetentionSweeper disabled(Path storeDir) {
        return new TranscriptRetentionSweeper(storeDir, null);
    }

    /** True when a retention window is configured. */
    public boolean isEnabled() {
        return retention != null;
    }

    /** Starts the background sweep. No-op when no retention is configured. */
    public synchronized void start() {
        if (running || !isEnabled()) {
            if (!isEnabled()) {
                log.debug("Transcript retention not configured; sweeper not started");
            }
            return;
        }
        running = true;
        worker = Thread.ofVirtual().name("transcript-retention").start(this::loop);
        log.info("Transcript retention sweeping every {} — deleting transcripts older than {}",
                interval, retention);
    }

    public synchronized void stop() {
        running = false;
        Thread t = worker;
        if (t != null) t.interrupt();
        worker = null;
    }

    public boolean isRunning() {
        return running;
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
                sweep();
            } catch (RuntimeException e) {
                log.warn("Transcript retention sweep failed", e);
            }
        }
    }

    /**
     * Deletes day-partitions older than the retention window, across all tenants.
     *
     * <p>Exposed so an operator endpoint or a test can force a sweep. Safe to call
     * when disabled — it returns zero without touching the filesystem.
     *
     * @return how many transcript files were deleted
     */
    public int sweep() {
        if (!isEnabled() || !Files.isDirectory(storeDir)) return 0;

        LocalDate cutoff = LocalDate.ofInstant(clock.instant().minus(retention), ZoneOffset.UTC);
        int deleted = 0;

        for (Path tenantDir : listDirs(storeDir)) {
            for (Path dateDir : listDirs(tenantDir)) {
                LocalDate partition = parseDate(dateDir.getFileName().toString());
                // Unparseable directory names are left alone: this sweeper only
                // removes partitions it positively understands.
                if (partition == null || !partition.isBefore(cutoff)) continue;
                deleted += deleteDirectory(dateDir);
            }
        }

        if (deleted > 0) {
            log.info("Transcript retention deleted {} transcript(s) older than {} (before {})",
                    deleted, retention, cutoff);
        }
        return deleted;
    }

    private List<Path> listDirs(Path parent) {
        try (Stream<Path> s = Files.list(parent)) {
            return s.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            log.debug("Could not list {}", parent, e);
            return List.of();
        }
    }

    private static LocalDate parseDate(String name) {
        try {
            return LocalDate.parse(name);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Deletes a day-partition, returning how many transcript files went with it. */
    private int deleteDirectory(Path dir) {
        int files = 0;
        List<Path> toDelete = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            // Deepest-first so directories are empty by the time they are removed.
            toDelete = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
        } catch (IOException e) {
            log.warn("Could not enumerate transcript partition {}", dir, e);
            return 0;
        }
        for (Path p : toDelete) {
            try {
                boolean isFile = Files.isRegularFile(p);
                if (Files.deleteIfExists(p) && isFile) files++;
            } catch (IOException e) {
                // One undeletable file must not abort the whole sweep.
                log.debug("Could not delete {}", p, e);
            }
        }
        return files;
    }
}
