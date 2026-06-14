package io.jaiclaw.agentmind.memory.web;

import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;
import io.jaiclaw.core.tenant.TenantGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Read-only debug endpoints for AGENT- and PEER-scope Memory records. Gated
 * off by default ({@code jaiclaw.agentmind.memory.rest.enabled=false}).
 * Intended for ops use only — production consumers should rely on the
 * prompt-injector path, not REST reads, to honour the prefix-cache
 * invariant.
 *
 * <p>Plan §6 task 2.8.
 */
@RestController
@RequestMapping("/api/agentmind/memory")
public class MemoryDebugController {

    private final AgentMindMemoryProvider memoryProvider;
    private final TenantGuard tenantGuard;

    public MemoryDebugController(AgentMindMemoryProvider memoryProvider, TenantGuard tenantGuard) {
        this.memoryProvider = memoryProvider;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping("/agent/{agentId}")
    public ResponseEntity<MemoryDocument> readAgentMemory(@PathVariable String agentId) {
        Optional<MemoryDocument> doc = memoryProvider.findMemory(
                resolveTenantId(), MemoryScope.AGENT, agentId, null);
        return doc.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/peer/{agentId}/{peerId}")
    public ResponseEntity<MemoryDocument> readPeerMemory(
            @PathVariable String agentId,
            @PathVariable String peerId) {
        Optional<MemoryDocument> doc = memoryProvider.findMemory(
                resolveTenantId(), MemoryScope.PEER, agentId, peerId);
        return doc.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String resolveTenantId() {
        if (tenantGuard == null || !tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }
}
