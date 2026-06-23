package io.jaiclaw.pipeline.actuator

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.pipeline.OutputDefinition
import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineRegistry
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.tracking.PipelineExecutionTracker
import spock.lang.Specification

import java.time.Duration

/**
 * SEV-010 regression guard — verifies the multi-tenant visibility
 * filter on {@code PipelineActuatorEndpoint}.
 *
 * <p>Before the fix, anyone with access to {@code /actuator/pipelines/{id}}
 * could read execution history (including failure reasons) for ALL
 * tenants when the gateway was running in multi-tenant mode. After the
 * fix:
 *
 * <ul>
 *   <li>SINGLE mode (or no TenantGuard): no filter — all executions
 *       visible. Behavior unchanged.</li>
 *   <li>MULTI mode: execution data is filtered to the current
 *       thread's tenant. Cross-tenant lookups (whether bulk or by-id)
 *       return as if not found.</li>
 * </ul>
 */
class PipelineActuatorEndpointTenantFilterSpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()
    PipelineExecutionTracker tracker = new PipelineExecutionTracker(10)

    private static PipelineDefinition pipe(String id) {
        return new PipelineDefinition(id, id + " name", null, List.of(), true,
                null, null, 0, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.LOG, null, null, null), null)
    }

    private static PipelineContext ctx(String executionId, String pipelineId, String tenantId) {
        return new PipelineContext(pipelineId, executionId, tenantId, "corr",
                0, 1, null, null, Map.of(), Map.of())
    }

    private static TenantGuard guard(TenantMode mode) {
        return new TenantGuard(new TenantProperties(mode, "default", false))
    }

    def cleanup() {
        TenantContextHolder.clear()
    }

    // ── SINGLE mode + no guard: existing behavior preserved ────────────

    def "SINGLE mode returns all executions regardless of tenant context"() {
        given:
        PipelineActuatorEndpoint endpoint = new PipelineActuatorEndpoint(
                registry, tracker, guard(TenantMode.SINGLE))
        registry.register(pipe("a"))
        tracker.started(ctx("e-acme", "a", "acme"))
        tracker.succeeded(ctx("e-acme", "a", "acme"), Duration.ofMillis(100))
        tracker.started(ctx("e-other", "a", "other"))
        tracker.succeeded(ctx("e-other", "a", "other"), Duration.ofMillis(100))

        when:
        Map<String, Object> result = endpoint.byId("a")

        then: "both tenants visible in SINGLE mode"
        List<Map> recent = result.get("recentExecutions") as List
        recent.size() == 2
    }

    def "null TenantGuard means no filtering (back-compat ctor)"() {
        given:
        PipelineActuatorEndpoint endpoint = new PipelineActuatorEndpoint(
                registry, tracker)  // 2-arg ctor → tenantGuard=null
        registry.register(pipe("a"))
        tracker.started(ctx("e-acme", "a", "acme"))
        tracker.succeeded(ctx("e-acme", "a", "acme"), Duration.ofMillis(100))
        tracker.started(ctx("e-other", "a", "other"))
        tracker.succeeded(ctx("e-other", "a", "other"), Duration.ofMillis(100))

        when:
        Map<String, Object> result = endpoint.byId("a")

        then: "both tenants visible — no guard means no filter"
        List<Map> recent = result.get("recentExecutions") as List
        recent.size() == 2
    }

    // ── MULTI mode: filter to current tenant ──────────────────────────

    def "MULTI mode filters byId recentExecutions to current tenant"() {
        given:
        PipelineActuatorEndpoint endpoint = new PipelineActuatorEndpoint(
                registry, tracker, guard(TenantMode.MULTI))
        registry.register(pipe("a"))
        tracker.started(ctx("e-acme", "a", "acme"))
        tracker.succeeded(ctx("e-acme", "a", "acme"), Duration.ofMillis(100))
        tracker.started(ctx("e-other", "a", "other"))
        tracker.succeeded(ctx("e-other", "a", "other"), Duration.ofMillis(100))
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))

        when:
        Map<String, Object> result = endpoint.byId("a")

        then: "only the current tenant's executions visible"
        List<Map> recent = result.get("recentExecutions") as List
        recent.size() == 1
        recent[0].get("tenantId") == "acme"
    }

    def "MULTI mode treats cross-tenant executionById lookup as not-found"() {
        given:
        PipelineActuatorEndpoint endpoint = new PipelineActuatorEndpoint(
                registry, tracker, guard(TenantMode.MULTI))
        registry.register(pipe("a"))
        tracker.started(ctx("e-other", "a", "other"))
        tracker.failed(ctx("e-other", "a", "other"), "sensitive failure reason",
                Duration.ofMillis(100))
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))

        when:
        Map<String, Object> result = endpoint.executionById("a", "e-other")

        then: "returns not-found rather than leaking the failure reason"
        result.containsKey("error")
        (result.get("error") as String).contains("not found")
        // Do not leak the failure reason in any field
        !result.containsKey("failureReason")
    }

    def "MULTI mode returns own tenant's executionById lookup"() {
        given:
        PipelineActuatorEndpoint endpoint = new PipelineActuatorEndpoint(
                registry, tracker, guard(TenantMode.MULTI))
        registry.register(pipe("a"))
        tracker.started(ctx("e-acme", "a", "acme"))
        tracker.succeeded(ctx("e-acme", "a", "acme"), Duration.ofMillis(100))
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))

        when:
        Map<String, Object> result = endpoint.executionById("a", "e-acme")

        then: "own tenant's execution is fully returned"
        result.get("executionId") == "e-acme"
        result.get("tenantId") == "acme"
        result.get("status") == "SUCCESS"
    }
}
