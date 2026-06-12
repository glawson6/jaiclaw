package io.jaiclaw.kanban.web;

import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.core.tenant.TenantContextPropagator;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.kanban.KanbanProperties;
import io.jaiclaw.kanban.events.TaskStateChanged;
import io.jaiclaw.kanban.model.BoardSnapshot;
import io.jaiclaw.kanban.service.BoardSnapshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
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
 * Holds {@link SseEmitter}s grouped by {@code (tenantId, boardId)} and
 * fans out {@link TaskStateChanged} events to each. Also runs a periodic
 * heartbeat (configurable via
 * {@link KanbanProperties.Sse#heartbeatSeconds()}) so proxies and load
 * balancers don't close idle connections.
 *
 * <p>Async fan-out is wrapped with {@link TenantContextPropagator} so any
 * tenant-aware code downstream (currently just the
 * {@link BoardSnapshotService} call on connect) sees the right context.
 *
 * <p>Lifecycle: {@link SmartLifecycle} so the heartbeat scheduler is shut
 * down cleanly on application stop.
 */
public class KanbanEventBroadcaster implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(KanbanEventBroadcaster.class);

    private final BoardSnapshotService snapshotService;
    private final TenantGuard tenantGuard;
    private final KanbanProperties.Sse sseProperties;
    private final ConcurrentMap<Key, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> connectionsByTenant = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "kanban-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });
    private volatile boolean running = false;

    public KanbanEventBroadcaster(BoardSnapshotService snapshotService,
                                  TenantGuard tenantGuard,
                                  KanbanProperties.Sse sseProperties) {
        this.snapshotService = snapshotService;
        this.tenantGuard = tenantGuard;
        this.sseProperties = sseProperties;
    }

    /**
     * Register a new emitter for the given board, sending the initial
     * {@code snapshot} event. Returns {@code null} when the per-tenant
     * connection cap would be exceeded — the caller should map that to
     * an HTTP {@code 429 Too Many Requests}.
     */
    public SseEmitter register(String boardId, SseEmitter emitter) {
        String tenantId = currentTenantId();
        int after = connectionsByTenant.merge(tenantId, 1, Integer::sum);
        if (after > sseProperties.maxConnections()) {
            connectionsByTenant.merge(tenantId, -1, Integer::sum);
            log.debug("Rejecting SSE connect for tenant {} — at max ({})",
                    tenantId, sseProperties.maxConnections());
            return null;
        }
        Key key = new Key(tenantId, boardId);
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> deregister(key, emitter));
        emitter.onTimeout(() -> deregister(key, emitter));
        emitter.onError(throwable -> deregister(key, emitter));
        sendInitialSnapshot(emitter, boardId);
        return emitter;
    }

    /** Subscribes to Spring events on the publisher thread. */
    @EventListener
    public void onTaskStateChanged(TaskStateChanged event) {
        Key key = new Key(event.tenantId(), event.boardId());
        List<SseEmitter> targets = emitters.get(key);
        if (targets == null || targets.isEmpty()) return;
        // Snapshot to avoid touching a CopyOnWriteArrayList mid-iteration on remove paths.
        List<SseEmitter> snapshot = new ArrayList<>(targets);
        // The application event fires on the publisher's thread, which already
        // has the correct TenantContext — sending synchronously inside
        // SseEmitter is cheap (it buffers to the servlet output stream). Spring
        // handles concurrent emitters internally.
        for (SseEmitter emitter : snapshot) {
            try {
                emitter.send(SseEmitter.event()
                        .name("state-changed")
                        .data(event));
            } catch (IOException | IllegalStateException e) {
                log.debug("Dropping SSE emitter due to send failure: {}", e.getMessage());
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
        // Heartbeat ticks on the scheduler thread — wrap with the propagator
        // so any tenant-aware downstream code sees the captured context.
        // Capture happens at scheduleAtFixedRate-time, which is the bootstrap
        // thread (no tenant context), so the heartbeat itself remains
        // tenant-agnostic — correct, because heartbeats are connection-level.
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
        // Stop before Tomcat starts winding down so emitters are completed first.
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

    private void sendInitialSnapshot(SseEmitter emitter, String boardId) {
        try {
            BoardSnapshot snapshot = snapshotService.snapshot(boardId).orElse(null);
            if (snapshot == null) return;
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException | IllegalStateException e) {
            log.debug("Initial snapshot send failed: {}", e.getMessage());
        }
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
        if (tenantGuard != null && tenantGuard.isMultiTenant()) {
            return tenantGuard.requireTenantIfMulti();
        }
        return tenantGuard != null ? tenantGuard.getProperties().defaultTenantId() : "default";
    }

    private record Key(String tenantId, String boardId) {
        Key {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(boardId, "boardId");
        }
    }
}
