package io.jaiclaw.pipeline.authoring;

import io.jaiclaw.core.hook.event.PipelineDeployedEvent;
import io.jaiclaw.core.hook.event.PipelineUndeployedEvent;
import io.jaiclaw.pipeline.AgentStageProcessor;
import io.jaiclaw.pipeline.BeanStageProcessor;
import io.jaiclaw.pipeline.CamelStageProcessor;
import io.jaiclaw.pipeline.PipelineAuditor;
import io.jaiclaw.pipeline.PipelineDefinition;
import io.jaiclaw.pipeline.PipelineHookFirer;
import io.jaiclaw.pipeline.PipelineMetrics;
import io.jaiclaw.pipeline.PipelineProperties;
import io.jaiclaw.pipeline.PipelineRegistry;
import io.jaiclaw.pipeline.PipelineRouteBuilder;
import io.jaiclaw.pipeline.PipelineSecurityGuard;
import io.jaiclaw.pipeline.PipelineTransportAuthenticator;
import io.jaiclaw.pipeline.gateway.PipelineSyncCoordinator;
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker;
import io.jaiclaw.pipeline.validation.ValidationReport;
import io.jaiclaw.pipeline.validation.PipelineValidator;
import io.jaiclaw.plugin.HookRunner;
import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.spi.RouteController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Hot-deploys, undeploys, and redeploys {@link PipelineDefinition}
 * instances against a live Camel context. Implements the semantics
 * documented in {@code docs/dev/pipeline/PIPELINE_HOT_RELOAD.md}.
 *
 * <p>Single-JVM only — multi-replica coordination is out of scope
 * (see the design doc § 7). All operations are serialised per
 * pipelineId via a {@link ReentrantLock} map; different pipelines
 * proceed in parallel.
 */
public class PipelineLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(PipelineLifecycleManager.class);

    private final PipelineRegistry registry;
    private final PipelineValidator validator;
    private final PipelineProperties properties;
    private final CamelContext camelContext;
    private final AgentStageProcessor agentProcessor;
    private final BeanStageProcessor beanProcessor;
    private final CamelStageProcessor camelProcessor;
    private final PipelineAuditor auditor;
    private final PipelineHookFirer hookFirer;
    private final PipelineMetrics metrics;
    private final PipelineSecurityGuard securityGuard;
    private final PipelineTransportAuthenticator transportAuthenticator;
    private final PipelineExecutionTracker tracker;
    private final PipelineSyncCoordinator syncCoordinator;
    private final HookRunner hookRunner;
    private final ApplicationEventPublisher publisher;
    private final Duration drainTimeout;

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** Full constructor. */
    public PipelineLifecycleManager(
            PipelineRegistry registry,
            PipelineValidator validator,
            PipelineProperties properties,
            CamelContext camelContext,
            AgentStageProcessor agentProcessor,
            BeanStageProcessor beanProcessor,
            CamelStageProcessor camelProcessor,
            PipelineAuditor auditor,
            PipelineHookFirer hookFirer,
            PipelineMetrics metrics,
            PipelineSecurityGuard securityGuard,
            PipelineTransportAuthenticator transportAuthenticator,
            PipelineExecutionTracker tracker,
            PipelineSyncCoordinator syncCoordinator,
            HookRunner hookRunner,
            ApplicationEventPublisher publisher,
            Duration drainTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.properties = properties;
        this.camelContext = Objects.requireNonNull(camelContext, "camelContext");
        this.agentProcessor = agentProcessor;
        this.beanProcessor = Objects.requireNonNull(beanProcessor, "beanProcessor");
        this.camelProcessor = camelProcessor;
        this.auditor = auditor;
        this.hookFirer = hookFirer;
        this.metrics = metrics;
        this.securityGuard = securityGuard;
        this.transportAuthenticator = transportAuthenticator;
        this.tracker = tracker;
        this.syncCoordinator = syncCoordinator;
        this.hookRunner = hookRunner;
        this.publisher = publisher;
        this.drainTimeout = drainTimeout == null ? Duration.ofSeconds(10) : drainTimeout;
    }

    /**
     * Deploy a new pipeline. Rejects an already-deployed id — the caller
     * must call {@link #redeploy(String, PipelineDefinition, String)}
     * explicitly to replace.
     *
     * @param definition the definition to deploy
     * @param origin     where the definition came from
     *                   ({@code STUDIO}, {@code YAML_IMPORT}, {@code CODE_BEAN})
     * @throws AlreadyDeployedException if a pipeline is already registered under this id
     * @throws IllegalArgumentException if the definition fails validation
     */
    public PipelineDefinition deploy(PipelineDefinition definition, String origin) {
        Objects.requireNonNull(definition, "definition");
        ReentrantLock lock = lockFor(definition.id());
        lock.lock();
        try {
            if (registry.contains(definition.id())) {
                throw new AlreadyDeployedException(definition.id());
            }
            ValidationReport report = validator.validate(definition);
            if (report.hasErrors()) {
                throw new IllegalArgumentException(
                        "Cannot deploy: " + report.formatted());
            }
            registry.replace(definition);
            try {
                buildAndAddRoutes(definition);
            } catch (Exception e) {
                registry.unregister(definition.id());
                throw new IllegalStateException(
                        "Failed to add Camel routes for pipeline '" + definition.id() + "': "
                                + e.getMessage(), e);
            }
            PipelineDeployedEvent event = PipelineDeployedEvent.of(
                    definition.id(),
                    firstTenant(definition),
                    definition.stages() == null ? 0 : definition.stages().size(),
                    origin == null ? "STUDIO" : origin);
            if (hookRunner != null) hookRunner.fireVoid(event);
            if (publisher != null) publisher.publishEvent(event);
            log.info("Deployed pipeline '{}' ({} stages, origin={})",
                    definition.id(), event.stageCount(), event.origin());
            return definition;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Undeploy a currently-registered pipeline. Drains in-flight
     * executions with a bounded timeout (see
     * {@code jaiclaw.pipeline.authoring.hot-reload.drain-timeout}),
     * removes every {@code pipeline-{id}-*} route from the Camel
     * context, and unregisters the definition.
     *
     * @param id     the pipeline id
     * @param reason lifecycle reason ({@code undeploy}, {@code redeploy}, {@code reset})
     * @return the removed definition, or empty if the id was not deployed
     */
    public Optional<PipelineDefinition> undeploy(String id, String reason) {
        if (id == null || id.isBlank()) return Optional.empty();
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            PipelineDefinition existing = registry.get(id);
            if (existing == null) return Optional.empty();
            stopAndRemoveRoutes(id);
            registry.unregister(id);
            PipelineUndeployedEvent event = PipelineUndeployedEvent.of(
                    id, firstTenant(existing), reason == null ? "undeploy" : reason);
            if (hookRunner != null) hookRunner.fireVoid(event);
            if (publisher != null) publisher.publishEvent(event);
            log.info("Undeployed pipeline '{}' (reason={})", id, event.reason());
            return Optional.of(existing);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Replace a deployed pipeline with a new definition. Two-step:
     * undeploy (with {@code reason=redeploy}), then deploy.
     * See design doc § 5 — not atomic; there is a brief window where
     * the id is neither registered nor deployed.
     */
    public PipelineDefinition redeploy(String id, PipelineDefinition next, String origin) {
        Objects.requireNonNull(next, "next");
        if (!Objects.equals(id, next.id())) {
            throw new IllegalArgumentException(
                    "Redeploy id mismatch: path '" + id + "' vs body '" + next.id() + "'");
        }
        ReentrantLock lock = lockFor(id);
        lock.lock();
        try {
            undeploy(id, "redeploy");
            return deploy(next, origin);
        } finally {
            lock.unlock();
        }
    }

    /** True if a pipeline is currently registered under this id. */
    public boolean isDeployed(String id) {
        return id != null && registry.contains(id);
    }

    // ── internals ────────────────────────────────────

    private void buildAndAddRoutes(PipelineDefinition definition) throws Exception {
        PipelineRouteBuilder routeBuilder = new PipelineRouteBuilder(
                definition,
                properties == null ? null : properties.defaults(),
                agentProcessor,
                beanProcessor,
                camelProcessor,
                auditor,
                hookFirer,
                metrics,
                securityGuard,
                transportAuthenticator,
                tracker,
                syncCoordinator);
        camelContext.addRoutes(routeBuilder);
    }

    private void stopAndRemoveRoutes(String pipelineId) {
        String prefix = "pipeline-" + pipelineId + "-";
        RouteController controller = camelContext.getRouteController();
        List<String> routeIds = new ArrayList<>();
        for (Route r : camelContext.getRoutes()) {
            if (r.getId() != null && r.getId().startsWith(prefix)) {
                routeIds.add(r.getId());
            }
        }
        for (String routeId : routeIds) {
            try {
                controller.stopRoute(routeId, drainTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.warn("Timed out stopping route {} after {} — forcing shutdown: {}",
                        routeId, drainTimeout, e.getMessage());
            }
            try {
                camelContext.removeRoute(routeId);
            } catch (Exception e) {
                log.warn("Failed to remove route {}: {}", routeId, e.getMessage());
            }
        }
    }

    private ReentrantLock lockFor(String id) {
        return locks.computeIfAbsent(id, k -> new ReentrantLock());
    }

    private static String firstTenant(PipelineDefinition definition) {
        if (definition == null || definition.tenantIds() == null || definition.tenantIds().isEmpty()) {
            return null;
        }
        return definition.tenantIds().get(0);
    }

    /** Thrown by {@link #deploy} when the id is already registered. */
    public static class AlreadyDeployedException extends RuntimeException {
        private final String pipelineId;

        public AlreadyDeployedException(String pipelineId) {
            super("Pipeline '" + pipelineId + "' is already deployed — call redeploy to replace");
            this.pipelineId = pipelineId;
        }

        public String pipelineId() { return pipelineId; }
    }
}
