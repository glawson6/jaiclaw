package io.jaiclaw.pipeline.web;

import io.jaiclaw.core.hook.event.HookEvent;
import io.jaiclaw.core.hook.event.PipelineExecutionCompletedEvent;
import io.jaiclaw.core.hook.event.PipelineExecutionFailedEvent;
import io.jaiclaw.core.hook.event.PipelineExecutionStartedEvent;
import io.jaiclaw.core.hook.event.PipelineStageCompletedEvent;
import io.jaiclaw.core.hook.event.PipelineStageFailedEvent;
import io.jaiclaw.core.hook.event.PipelineStageStartedEvent;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantContextPropagator;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.pipeline.PipelineProperties;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.tracking.PipelineExecutionSummary;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Holds {@link SseEmitter}s grouped by {@code (tenantId, pipelineId)} and
 * fans out typed pipeline lifecycle events to each. A sentinel pipeline
 * id {@link #GLOBAL_PIPELINE_ID} provides a whole-system stream — an
 * emitter registered under {@code GLOBAL} sees every event for its
 * tenant regardless of pipeline id.
 *
 * <p>Mirrors the kanban module's
 * {@code KanbanEventBroadcaster} — same {@code SmartLifecycle} shape,
 * same {@code TenantContextPropagator} handling on the heartbeat, same
 * emitter cleanup on send failure.
 *
 * <p>Async fan-out isn't used here — Spring's {@code @EventListener}
 * fires on the publisher thread, which already carries the correct
 * tenant context (see {@code TaskTransitionService} in the kanban
 * module for the analogous flow). The heartbeat scheduler runs on its
 * own single thread wrapped with {@link TenantContextPropagator}.
 *
 * <p>The broadcaster is optional — the whole class only wires when
 * {@code jaiclaw.pipeline.sse.enabled=true} (the default) and Spring
 * WebMVC is on the classpath.
 */
public class PipelineEventBroadcaster implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PipelineEventBroadcaster.class);

    /**
     * Sentinel pipeline id under which the whole-system stream is
     * registered. Emitters registered with this id receive every event
     * for their tenant.
     */
    public static final String GLOBAL_PIPELINE_ID = "*";

    private final PipelineRegistry registry;
    private final PipelineExecutionTracker tracker;
    private final TenantGuard tenantGuard;
    private final PipelineProperties.SseProperties sseProperties;
    private final ConcurrentMap<Key, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> connectionsByTenant = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "pipeline-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });
    private volatile boolean running = false;

    public PipelineEventBroadcaster(PipelineRegistry registry,
                                    PipelineExecutionTracker tracker,
                                    TenantGuard tenantGuard,
                                    PipelineProperties.SseProperties sseProperties) {
        this.registry = registry;
        this.tracker = tracker;
        this.tenantGuard = tenantGuard;
        this.sseProperties = sseProperties;
    }

    /**
     * Register a new emitter for the given pipeline id (use
     * {@link #GLOBAL_PIPELINE_ID} for the whole-system stream). When
     * {@code jaiclaw.pipeline.sse.include-snapshot=true} (default), the
     * emitter receives an initial {@code snapshot} event carrying the
     * pipeline's definition + recent executions so a UI can render
     * immediately.
     *
     * @return the same emitter, or {@code null} if the per-tenant
     *         connection cap would be exceeded (caller should map to
     *         HTTP 429)
     */
    public SseEmitter register(String pipelineId, SseEmitter emitter) {
        String tenantId = currentTenantId();
        int after = connectionsByTenant.merge(tenantId, 1, Integer::sum);
        if (after > sseProperties.maxConnections()) {
            connectionsByTenant.merge(tenantId, -1, Integer::sum);
            log.debug("Rejecting SSE connect for tenant {} — at max ({})",
                    tenantId, sseProperties.maxConnections());
            return null;
        }
        Key key = new Key(tenantId, pipelineId);
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> deregister(key, emitter));
        emitter.onTimeout(() -> deregister(key, emitter));
        emitter.onError(throwable -> deregister(key, emitter));
        if (sseProperties.includeSnapshot()) {
            sendInitialSnapshot(emitter, pipelineId);
        }
        return emitter;
    }

    // ── Event listeners — one per typed event record ────────────────

    @EventListener
    public void onExecutionStarted(PipelineExecutionStartedEvent e) {
        fanOut(e.pipelineId(), e.tenantId(), "execution-started", e);
    }

    @EventListener
    public void onExecutionCompleted(PipelineExecutionCompletedEvent e) {
        fanOut(e.pipelineId(), e.tenantId(), "execution-completed", e);
    }

    @EventListener
    public void onExecutionFailed(PipelineExecutionFailedEvent e) {
        fanOut(e.pipelineId(), e.tenantId(), "execution-failed", e);
    }

    @EventListener
    public void onStageStarted(PipelineStageStartedEvent e) {
        fanOut(e.pipelineId(), e.tenantId(), "stage-started", e);
    }

    @EventListener
    public void onStageCompleted(PipelineStageCompletedEvent e) {
        fanOut(e.pipelineId(), e.tenantId(), "stage-completed", e);
    }

    @EventListener
    public void onStageFailed(PipelineStageFailedEvent e) {
        fanOut(e.pipelineId(), e.tenantId(), "stage-failed", e);
    }

    /**
     * Fan an event out to every emitter subscribed under either the
     * exact pipeline id or the global sentinel, filtered by tenant.
     */
    private void fanOut(String pipelineId, String tenantId, String eventName, HookEvent payload) {
        // Normalize null tenant to the default so single-tenant deployments
        // fall into the same key namespace as multi-tenant ones.
        String tenant = tenantId != null ? tenantId : defaultTenantId();
        sendTo(new Key(tenant, pipelineId), eventName, payload);
        sendTo(new Key(tenant, GLOBAL_PIPELINE_ID), eventName, payload);
    }

    private void sendTo(Key key, String eventName, Object payload) {
        List<SseEmitter> targets = emitters.get(key);
        if (targets == null || targets.isEmpty()) return;
        List<SseEmitter> snapshot = new ArrayList<>(targets);
        for (SseEmitter emitter : snapshot) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                log.debug("Dropping SSE emitter due to send failure: {}", ex.getMessage());
                deregister(key, emitter);
            }
        }
    }

    public int connectionsForCurrentTenant() {
        return connectionsByTenant.getOrDefault(currentTenantId(), 0);
    }

    public int totalConnections() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    // ── SmartLifecycle ──────────────────────────────────────────────

    @Override
    public synchronized void start() {
        if (running) return;
        long interval = Math.max(1, sseProperties.heartbeatSeconds());
        heartbeatScheduler.scheduleAtFixedRate(
                TenantContextPropagator.wrap(this::heartbeatAllEmitters),
                interval, interval, TimeUnit.SECONDS);
        running = true;
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        running = false;
        heartbeatScheduler.shutdownNow();
        emitters.values().forEach(list -> list.forEach(SseEmitter::complete));
        emitters.clear();
        connectionsByTenant.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Stop before Tomcat winds down, matching kanban's ordering.
        return Integer.MAX_VALUE - 100;
    }

    // ── helpers ─────────────────────────────────────────────────────

    private void heartbeatAllEmitters() {
        for (Map.Entry<Key, List<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : new ArrayList<>(entry.getValue())) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (IOException | IllegalStateException e) {
                    deregister(entry.getKey(), emitter);
                }
            }
        }
    }

    private void sendInitialSnapshot(SseEmitter emitter, String pipelineId) {
        try {
            Map<String, Object> snapshot = buildSnapshot(pipelineId);
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException | IllegalStateException e) {
            log.debug("Initial pipeline snapshot send failed: {}", e.getMessage());
        }
    }

    /**
     * Build the initial snapshot payload for an emitter. For a specific
     * pipeline id, includes that pipeline's definition + its recent
     * executions. For {@link #GLOBAL_PIPELINE_ID}, includes every
     * registered pipeline id (definitions only — not their history, to
     * keep the initial event bounded).
     */
    private Map<String, Object> buildSnapshot(String pipelineId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pipelineId", pipelineId);
        if (GLOBAL_PIPELINE_ID.equals(pipelineId)) {
            List<Map<String, Object>> pipelines = new ArrayList<>();
            if (registry != null) {
                registry.getAll().forEach(def -> {
                    Map<String, Object> summary = new LinkedHashMap<>();
                    summary.put("id", def.id());
                    summary.put("name", def.name());
                    summary.put("stageCount", def.stages() == null ? 0 : def.stages().size());
                    pipelines.add(summary);
                });
            }
            out.put("pipelines", pipelines);
        } else if (registry != null) {
            var def = registry.get(pipelineId);
            if (def != null) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", def.id());
                summary.put("name", def.name());
                summary.put("stageCount", def.stages() == null ? 0 : def.stages().size());
                if (def.stages() != null) {
                    summary.put("stageNames", def.stages().stream().map(s -> s.name()).toList());
                }
                out.put("definition", summary);
            }
            if (tracker != null) {
                List<PipelineExecutionSummary> recent = tracker.recent(pipelineId);
                out.put("recentExecutions", recent == null ? List.of() : recent);
            }
        }
        return out;
    }

    private void deregister(Key key, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(key);
        if (list != null && list.remove(emitter)) {
            connectionsByTenant.merge(key.tenantId(), -1, (oldValue, delta) -> {
                int next = oldValue + delta;
                return next <= 0 ? null : next;
            });
            if (list.isEmpty()) emitters.remove(key, list);
        }
    }

    private String currentTenantId() {
        if (TenantContextHolder.get() != null) {
            return TenantContextHolder.get().getTenantId();
        }
        return defaultTenantId();
    }

    private String defaultTenantId() {
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantGuard != null ? tenantGuard.getProperties().defaultTenantId() : "default";
    }

    private record Key(String tenantId, String pipelineId) {
        Key {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(pipelineId, "pipelineId");
        }
    }
}
