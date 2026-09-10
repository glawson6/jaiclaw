package io.jaiclaw.agent.delegation;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.config.DelegationProperties;
import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.hook.event.SubAgentEndedEvent;
import io.jaiclaw.core.hook.event.SubAgentStartedEvent;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.tenant.TenantContextPropagator;
import io.jaiclaw.core.tool.ToolProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Default {@link SubAgentLauncher}: runs children on the same {@link AgentRuntime}
 * as their parent, on virtual threads, under the parent's tenant.
 *
 * <p>Reusing {@code AgentRuntime} rather than introducing a second runtime type
 * is what makes the Phase 1 guards apply to children automatically — a child gets
 * budgets, the repetition guard and approval floors for free.
 *
 * <h2>Limits</h2>
 * <ul>
 *   <li><strong>Depth</strong> is refused, not queued. A depth violation is a
 *       property of the call graph, so waiting can never resolve it.</li>
 *   <li><strong>Concurrency</strong> queues on a per-parent semaphore. Being busy
 *       is transient, and the caller's wait timeout already bounds the block, so a
 *       saturated parent degrades to a RUNNING handle rather than an error the
 *       model would retry blindly.</li>
 * </ul>
 *
 * <h2>Tool profile</h2>
 * A child can never be granted a wider profile than its parent. Profiles are an
 * enum rather than a set, so "narrower" is defined by an explicit privilege
 * ordering — see {@link #narrowest}.
 *
 * <p>Phase 2 of the 1.2.0 plan.
 */
@Experimental
public class DefaultSubAgentLauncher implements SubAgentLauncher {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubAgentLauncher.class);

    private final AgentRuntime agentRuntime;
    private final SessionManager sessionManager;
    private final DelegationProperties properties;
    private final AgentHookDispatcher hooks;

    private final Map<String, SubAgentHandle> handles = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> parentSemaphores = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> childCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> activeCounts = new ConcurrentHashMap<>();

    public DefaultSubAgentLauncher(AgentRuntime agentRuntime,
                                   SessionManager sessionManager,
                                   DelegationProperties properties,
                                   AgentHookDispatcher hooks) {
        this.agentRuntime = agentRuntime;
        this.sessionManager = sessionManager;
        this.properties = properties;
        this.hooks = hooks;
    }

    @Override
    public SubAgentHandle launch(SubAgentRequest request) {
        if (request == null || request.hasBlankGoal()) {
            return refusedHandle("A non-empty 'goal' is required to delegate a task.");
        }
        AgentRuntimeContext parent = request.parentContext();
        if (parent == null) {
            return refusedHandle("Delegation requires a parent runtime context.");
        }

        int childDepth = parent.delegationDepth() + 1;
        if (childDepth > properties.maxDepth()) {
            // Permanent, not transient: say so plainly so the model stops trying.
            return refusedHandle("Delegation depth limit reached (max " + properties.maxDepth()
                    + "). This task is already " + parent.delegationDepth()
                    + " level(s) deep — do the work directly instead of delegating further.");
        }

        String parentSessionKey = parent.sessionKey();
        String handleId = UUID.randomUUID().toString();
        String childSessionKey = nextChildSessionKey(parent);

        ToolProfile childProfile = narrowest(
                request.toolProfile() != null ? request.toolProfile() : properties.defaultChildProfile(),
                parent.toolProfile());

        int childBudget = request.maxIterations() > 0
                ? request.maxIterations()
                : properties.childMaxIterations();

        Semaphore permits = parentSemaphores.computeIfAbsent(
                parentSessionKey == null ? "" : parentSessionKey,
                k -> new Semaphore(properties.maxConcurrent()));

        CompletableFuture<SubAgentResult> future = new CompletableFuture<>();
        SubAgentHandle handle = new SubAgentHandle(handleId, childSessionKey, future, Instant.now());
        handles.put(handleId, handle);

        // TenantContextPropagator carries the parent's tenant onto the virtual
        // thread; without it the child would run tenant-less and could read or
        // write the wrong tenant's data.
        Runnable body = TenantContextPropagator.wrap(() ->
                runChild(request, handle, permits, parent, childSessionKey,
                        childProfile, childBudget, childDepth));

        Thread.ofVirtual()
                .name("subagent-" + handleId.substring(0, 8))
                .start(body);

        return handle;
    }

    private void runChild(SubAgentRequest request, SubAgentHandle handle, Semaphore permits,
                          AgentRuntimeContext parent, String childSessionKey,
                          ToolProfile childProfile, int childBudget, int childDepth) {
        String handleId = handle.id();
        String parentSessionKey = parent.sessionKey();
        boolean acquired = false;
        try {
            // Queue rather than refuse — see the class javadoc.
            permits.acquire();
            acquired = true;

            if (handle.future().isCancelled()) return;

            activeCounts.computeIfAbsent(keyOf(parentSessionKey), k -> new AtomicInteger())
                    .incrementAndGet();

            Session childSession = sessionManager.getOrCreate(childSessionKey, parent.agentId());
            AgentRuntimeContext childContext = AgentRuntimeContext.builder()
                    .agentId(parent.agentId())
                    .sessionKey(childSessionKey)
                    .session(childSession)
                    .identity(parent.identity())
                    .toolProfile(childProfile)
                    .workspaceDir(parent.workspaceDir())
                    .tenantConfig(parent.tenantConfig())
                    .delegationDepth(childDepth)
                    .parentSessionKey(parentSessionKey)
                    .build();

            fire(() -> SubAgentStartedEvent.of(parent.agentId(), childSessionKey, handleId,
                    parentSessionKey, request.goal(), childDepth, childBudget));

            log.debug("Subagent {} starting — session={} depth={} profile={} budget={}",
                    handleId, childSessionKey, childDepth, childProfile, childBudget);

            AssistantMessage answer = agentRuntime.run(request.toPrompt(), childContext).join();
            String summary = answer == null ? null : answer.content();

            SubAgentResult result = SubAgentResult.completed(handleId, childSessionKey, summary, childBudget);
            handle.future().complete(result);
            fire(() -> SubAgentEndedEvent.of(parent.agentId(), childSessionKey, handleId,
                    parentSessionKey, SubAgentStatus.COMPLETED.name(), summary, childBudget, null));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handle.future().complete(SubAgentResult.cancelled(handleId, childSessionKey, 0));
            fire(() -> SubAgentEndedEvent.of(parent.agentId(), childSessionKey, handleId,
                    parentSessionKey, SubAgentStatus.CANCELLED.name(), null, 0,
                    "Interrupted before completion"));
        } catch (Throwable t) {
            String message = unwrap(t);
            log.warn("Subagent {} failed: {}", handleId, message);
            handle.future().complete(SubAgentResult.failed(handleId, childSessionKey, message, 0));
            fire(() -> SubAgentEndedEvent.of(parent.agentId(), childSessionKey, handleId,
                    parentSessionKey, SubAgentStatus.FAILED.name(), null, 0, message));
        } finally {
            if (acquired) {
                permits.release();
                AtomicInteger active = activeCounts.get(keyOf(parentSessionKey));
                if (active != null) active.decrementAndGet();
            }
            closeChildSession(childSessionKey);
        }
    }

    /**
     * Closes the child's session so it does not accumulate. Failure to close is
     * logged, never propagated — the child's answer is already delivered and
     * losing it over bookkeeping would be worse than a stale session.
     */
    private void closeChildSession(String childSessionKey) {
        try {
            sessionManager.close(childSessionKey);
        } catch (RuntimeException e) {
            log.debug("Could not close subagent session {}", childSessionKey, e);
        }
    }

    @Override
    public Optional<SubAgentHandle> find(String handleId) {
        return Optional.ofNullable(handleId).map(handles::get);
    }

    @Override
    public boolean cancel(String handleId) {
        SubAgentHandle handle = handles.get(handleId);
        return handle != null && handle.cancel();
    }

    @Override
    public int activeCount(String parentSessionKey) {
        AtomicInteger counter = activeCounts.get(keyOf(parentSessionKey));
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    /**
     * Child session key: {@code {agentId}:subagent:{parentSessionKey}:{n}}, with
     * {@code n} monotonic per parent so sibling children never collide.
     */
    private String nextChildSessionKey(AgentRuntimeContext parent) {
        String parentKey = keyOf(parent.sessionKey());
        int n = childCounters.computeIfAbsent(parentKey, k -> new AtomicInteger())
                .incrementAndGet();
        return parent.agentId() + ":subagent:" + parentKey + ":" + n;
    }

    /**
     * The less-privileged of two profiles. Delegates to {@link ToolProfile#narrowest}
     * so the ordering lives in one place — this class previously carried its own
     * copy, which would have silently ignored WEBHOOK_SAFE when that was added.
     */
    static ToolProfile narrowest(ToolProfile requested, ToolProfile parent) {
        return ToolProfile.narrowest(requested, parent);
    }

    private SubAgentHandle refusedHandle(String reason) {
        CompletableFuture<SubAgentResult> refused =
                CompletableFuture.completedFuture(SubAgentResult.refused(reason));
        return new SubAgentHandle(null, null, refused, Instant.now());
    }

    private static String keyOf(String sessionKey) {
        return sessionKey == null ? "" : sessionKey;
    }

    /** Fires a hook event, never letting a bad listener break the child run. */
    private void fire(java.util.function.Supplier<io.jaiclaw.core.hook.event.HookEvent> event) {
        if (hooks == null) return;
        try {
            hooks.fireVoid(event.get());
        } catch (RuntimeException e) {
            log.debug("Subagent hook listener failed", e);
        }
    }

    private static String unwrap(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
    }
}
