package io.jaiclaw.pipeline.gateway;

import io.jaiclaw.pipeline.PipelineProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates synchronous pipeline executions. The
 * {@link DefaultPipelineGateway} calls {@link #register(String)} to allocate
 * a {@link CompletableFuture} for an execution id; the
 * {@code PipelineRouteBuilder} output/exception routes call
 * {@link #complete(String, PipelineExecutionResult)} (success or failure-as-
 * result) to unblock the waiter.
 *
 * <p>A bounded {@link ConcurrentHashMap} caps the in-flight count
 * ({@code jaiclaw.pipeline.sync.max-pending}). When the cap is reached,
 * {@code register} returns an already-failed future carrying a
 * {@link PipelineCapacityException} — the gateway uses this to skip the
 * Camel send entirely.
 *
 * <p>A periodic sweep reaps entries older than
 * {@code jaiclaw.pipeline.sync.orphan-ttl}, completing them exceptionally
 * with a {@link PipelineOrphanException}. This bounds the leak when the
 * pipeline route fails to complete the future (JVM crash, ill-routed
 * exchange, etc.).
 *
 * <p>Completions are dispatched on a dedicated executor (size
 * {@code jaiclaw.pipeline.sync.completion-pool-size}) so caller-attached
 * continuations (e.g. {@code future.thenApply(...)}) do not run on the
 * Camel worker thread.
 */
public class PipelineSyncCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PipelineSyncCoordinator.class);

    private final PipelineProperties.SyncProperties properties;
    private final ConcurrentHashMap<String, PendingEntry> pending = new ConcurrentHashMap<>();
    private final ExecutorService completionExecutor;
    private final ScheduledExecutorService sweepExecutor;

    public PipelineSyncCoordinator(PipelineProperties.SyncProperties properties) {
        this.properties = properties != null ? properties : PipelineProperties.SyncProperties.DEFAULT;
        this.completionExecutor = Executors.newFixedThreadPool(
                this.properties.completionPoolSize(),
                namedThreadFactory("jaiclaw-pipeline-sync-complete"));
        this.sweepExecutor = Executors.newSingleThreadScheduledExecutor(
                namedThreadFactory("jaiclaw-pipeline-sync-sweep"));
        long intervalMs = this.properties.sweepInterval().toMillis();
        this.sweepExecutor.scheduleAtFixedRate(
                this::sweepOrphans, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.debug("PipelineSyncCoordinator started — maxPending={}, orphanTtl={}, sweepInterval={}, completionPoolSize={}",
                this.properties.maxPending(),
                this.properties.orphanTtl(),
                this.properties.sweepInterval(),
                this.properties.completionPoolSize());
    }

    /**
     * Register a pending future for {@code executionId}. Returns the future
     * the caller should await. When the in-flight count exceeds
     * {@code maxPending}, returns an already-failed future carrying a
     * {@link PipelineCapacityException} without adding to the map — the
     * gateway uses this to skip the Camel submission.
     */
    public CompletableFuture<PipelineExecutionResult> register(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("executionId must not be blank"));
        }
        int cap = properties.maxPending();
        if (pending.size() >= cap) {
            return CompletableFuture.failedFuture(new PipelineCapacityException(executionId, cap));
        }
        CompletableFuture<PipelineExecutionResult> future = new CompletableFuture<>();
        PendingEntry existing = pending.putIfAbsent(executionId, new PendingEntry(future, Instant.now()));
        if (existing != null) {
            // Re-registration on the same id: hand back the existing future so
            // the caller doesn't end up holding a stranded one.
            return existing.future();
        }
        return future;
    }

    /**
     * Complete a registered future with the result. No-op if the id is unknown
     * (e.g. the execution was fire-and-forget, the future was already reaped,
     * or {@link #complete} was called twice).
     */
    public void complete(String executionId, PipelineExecutionResult result) {
        if (executionId == null || result == null) return;
        PendingEntry entry = pending.remove(executionId);
        if (entry == null) return;
        CompletableFuture<PipelineExecutionResult> future = entry.future();
        if (future.isDone()) return;
        future.completeAsync(() -> result, completionExecutor);
    }

    /**
     * Complete a registered future exceptionally. No-op if the id is unknown.
     * Used for true infrastructure failures (orphan reaping) — ordinary stage
     * failures complete normally with {@code status=FAILED}.
     */
    public void completeExceptionally(String executionId, Throwable cause) {
        if (executionId == null || cause == null) return;
        PendingEntry entry = pending.remove(executionId);
        if (entry == null) return;
        CompletableFuture<PipelineExecutionResult> future = entry.future();
        if (future.isDone()) return;
        completionExecutor.execute(() -> future.completeExceptionally(cause));
    }

    /** Current in-flight pending count. */
    public int pendingCount() {
        return pending.size();
    }

    /** Configured maximum in-flight count. */
    public int capacity() {
        return properties.maxPending();
    }

    /** Reap entries older than {@code orphanTtl} — package-private for testability. */
    void sweepOrphans() {
        try {
            Duration ttl = properties.orphanTtl();
            Instant cutoff = Instant.now().minus(ttl);
            List<String> orphaned = new ArrayList<>();
            for (Map.Entry<String, PendingEntry> entry : pending.entrySet()) {
                if (entry.getValue().submittedAt().isBefore(cutoff)) {
                    orphaned.add(entry.getKey());
                }
            }
            for (String id : orphaned) {
                completeExceptionally(id, new PipelineOrphanException(id,
                        "Pipeline execution " + id + " exceeded orphan TTL " + ttl));
            }
            if (!orphaned.isEmpty()) {
                log.warn("Reaped {} orphaned pipeline sync execution(s) older than {}",
                        orphaned.size(), ttl);
            }
        } catch (Exception e) {
            log.warn("PipelineSyncCoordinator sweep failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Stop the sweep and completion executors and abandon any pending futures
     * with {@link PipelineOrphanException}. Wired as the bean
     * {@code destroyMethod} in {@code PipelineAutoConfiguration}.
     */
    public void shutdown() {
        sweepExecutor.shutdownNow();
        completionExecutor.shutdown();
        try {
            if (!completionExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                completionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            completionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (Map.Entry<String, PendingEntry> entry : pending.entrySet()) {
            CompletableFuture<PipelineExecutionResult> future = entry.getValue().future();
            if (!future.isDone()) {
                future.completeExceptionally(new PipelineOrphanException(
                        entry.getKey(),
                        "PipelineSyncCoordinator shutdown before execution completed"));
            }
        }
        pending.clear();
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicLong counter = new AtomicLong();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /** Internal: pending-future + submit-time for orphan TTL bookkeeping. */
    private record PendingEntry(CompletableFuture<PipelineExecutionResult> future, Instant submittedAt) {
    }
}
