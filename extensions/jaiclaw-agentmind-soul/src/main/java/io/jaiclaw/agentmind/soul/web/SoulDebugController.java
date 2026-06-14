package io.jaiclaw.agentmind.soul.web;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;
import io.jaiclaw.core.tenant.TenantGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Read-only debug endpoint for AGENT-scope Soul records. Gated off by default
 * ({@code jaiclaw.agentmind.soul.rest.enabled=false}). Intended for ops use
 * only — production consumers should rely on the prompt injector path, not
 * REST reads, to honour the prefix-cache invariant.
 *
 * <p>Plan §5 task 1.8.
 */
@RestController
@RequestMapping("/api/agentmind/soul")
public class SoulDebugController {

    private final SoulProvider soulProvider;
    private final TenantGuard tenantGuard;

    public SoulDebugController(SoulProvider soulProvider, TenantGuard tenantGuard) {
        this.soulProvider = soulProvider;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<Soul> readAgentSoul(@PathVariable String agentId) {
        Optional<Soul> soul = soulProvider.findSoul(resolveTenantId(), SoulScope.AGENT, agentId);
        return soul.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String resolveTenantId() {
        if (tenantGuard == null || !tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }
}
