package io.jaiclaw.config;

import io.jaiclaw.core.tool.ToolProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Subagent delegation policy, bound from {@code jaiclaw.agent.delegation}.
 *
 * <p>Delegation is <strong>off by default</strong>: with {@code enabled=false}
 * the {@code delegate_task} and {@code delegate_status} tools are not registered
 * at all, so no model can reach them.
 *
 * <p>Deliberately a top-level properties record rather than a 16th field on
 * {@code AgentProperties.AgentConfig}. Delegation is a runtime-wide safety
 * policy, and that nested record already carries documented Boot-4 binding
 * fragility (see the override plumbing in {@code TenantAgentConfigService}) that
 * there is no reason to add to.
 *
 * <p>Bound by the constructor binder, so exactly one public constructor —
 * programmatic defaults live on {@link #defaults()}.
 *
 * @param enabled            master switch; false means the tools are never registered
 * @param maxDepth           how deep delegation may nest. 1 means a parent may
 *                           delegate but its children may not. Depth violations are
 *                           permanent, so they are refused rather than queued.
 * @param maxConcurrent      simultaneous children per parent session. Overflow
 *                           <em>queues</em> — bounded by {@link #waitTimeout()} —
 *                           because being busy is transient.
 * @param childMaxIterations iteration budget for a child run. Independent of the
 *                           parent's, and smaller by default (the Hermes model:
 *                           a broad parent, cheap focused children).
 * @param defaultChildProfile tool profile a child gets when the caller does not ask
 *                           for one. Always narrowed to the parent's profile.
 * @param waitTimeout        how long a blocking {@code delegate_task} waits before
 *                           returning a RUNNING handle instead. Not a cancellation —
 *                           the child keeps going and can be polled by id.
 * @param kanbanEnabled      mirror subagent lifecycle onto a kanban card when
 *                           {@code jaiclaw-kanban} is on the classpath
 */
@ConfigurationProperties(prefix = "jaiclaw.agent.delegation")
public record DelegationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("2") int maxDepth,
        @DefaultValue("4") int maxConcurrent,
        @DefaultValue("50") int childMaxIterations,
        @DefaultValue("MINIMAL") ToolProfile defaultChildProfile,
        @DefaultValue("10m") Duration waitTimeout,
        @DefaultValue("false") boolean kanbanEnabled
) {

    public DelegationProperties {
        if (maxDepth < 0) maxDepth = 0;
        if (maxConcurrent <= 0) maxConcurrent = 1;
        if (childMaxIterations <= 0) childMaxIterations = 50;
        if (defaultChildProfile == null) defaultChildProfile = ToolProfile.MINIMAL;
        if (waitTimeout == null || waitTimeout.isNegative() || waitTimeout.isZero()) {
            waitTimeout = Duration.ofMinutes(10);
        }
    }

    /** Programmatic defaults for tests and builders; never seen by the binder. */
    public static DelegationProperties defaults() {
        return new DelegationProperties(false, 2, 4, 50,
                ToolProfile.MINIMAL, Duration.ofMinutes(10), false);
    }

    /** Defaults with delegation switched on — convenience for tests. */
    public static DelegationProperties enabledDefaults() {
        return new DelegationProperties(true, 2, 4, 50,
                ToolProfile.MINIMAL, Duration.ofMinutes(10), false);
    }
}
