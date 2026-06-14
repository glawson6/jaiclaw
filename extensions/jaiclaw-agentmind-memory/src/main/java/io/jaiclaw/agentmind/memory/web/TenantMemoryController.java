package io.jaiclaw.agentmind.memory.web;

import io.jaiclaw.agentmind.memory.AgentMindMemoryProperties;
import io.jaiclaw.agentmind.memory.overflow.MemoryOverflowPolicy;
import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.core.agent.MemoryOverflowException;
import io.jaiclaw.core.agent.StaleMemoryVersionException;
import io.jaiclaw.core.model.MemoryDocument;
import io.jaiclaw.core.model.MemoryScope;
import io.jaiclaw.core.tenant.TenantGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Operator-only write endpoint for TENANT-scope Memory records. Role-guarded
 * by {@code jaiclaw.agentmind.memory.tenant.write.roles} (default
 * {@code [ADMIN, OPERATOR]}). All endpoints gated by
 * {@code jaiclaw.agentmind.memory.tenant.enabled=true}.
 *
 * <p>Plan §6 task 2.17. Tenant Memory writes never need to go through the
 * agent tool path — this controller and the matching MCP tool are the
 * canonical authoring surface for institutional knowledge. The agent tool's
 * TENANT path remains gated by the
 * {@code agent-write-enabled} property and is intended for narrow cases
 * (e.g. an agent harvesting onboarding notes during a controlled session).
 */
@RestController
@RequestMapping("/api/agentmind/memory/tenant")
public class TenantMemoryController {

    private static final Logger log = LoggerFactory.getLogger(TenantMemoryController.class);

    private final AgentMindMemoryProvider memoryProvider;
    private final TenantGuard tenantGuard;
    private final MemoryOverflowPolicy overflowPolicy;
    private final AgentMindMemoryProperties props;
    private final List<String> writeRoles;

    public TenantMemoryController(AgentMindMemoryProvider memoryProvider,
                                  TenantGuard tenantGuard,
                                  MemoryOverflowPolicy overflowPolicy,
                                  AgentMindMemoryProperties props) {
        this.memoryProvider = memoryProvider;
        this.tenantGuard = tenantGuard;
        this.overflowPolicy = overflowPolicy;
        this.props = props;
        this.writeRoles = props.tenant().writeRoles();
    }

    @GetMapping
    public ResponseEntity<MemoryDocument> read() {
        Optional<MemoryDocument> doc = memoryProvider.findMemory(
                resolveTenantId(), MemoryScope.TENANT, null, null);
        return doc.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<?> upsert(@RequestBody TenantMemoryPayload payload, HttpServletRequest req) {
        if (!authorized(req)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Unauthorised: caller does not hold a required role. "
                            + "Roles checked: " + writeRoles);
        }
        if (payload == null || payload.content() == null) {
            return ResponseEntity.badRequest().body("content is required");
        }
        String tenantId = resolveTenantId();
        int budget = props.budgets().tenantChars();
        try {
            Optional<MemoryDocument> existing = memoryProvider.findMemory(
                    tenantId, MemoryScope.TENANT, null, null);
            long nextVersion = existing.map(d -> d.version() + 1L).orElse(0L);
            MemoryDocument toPersist = new MemoryDocument(MemoryScope.TENANT, tenantId, null, null,
                    payload.content(), budget, java.time.Instant.now(), nextVersion);

            if (toPersist.content().length() > budget) {
                toPersist = overflowPolicy.resolve(toPersist);
            }
            MemoryDocument saved = memoryProvider.saveMemory(toPersist);
            return ResponseEntity.ok(saved);
        } catch (MemoryOverflowException e) {
            log.warn("Tenant Memory write rejected by overflow policy for tenant={}: {}",
                    tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(e.getMessage());
        } catch (StaleMemoryVersionException e) {
            log.warn("Stale tenant-Memory write for tenant={}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<?> delete(HttpServletRequest req) {
        if (!authorized(req)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Unauthorised: caller does not hold a required role.");
        }
        memoryProvider.deleteMemory(resolveTenantId(), MemoryScope.TENANT, null, null);
        return ResponseEntity.noContent().build();
    }

    boolean authorized(HttpServletRequest req) {
        if (writeRoles == null || writeRoles.isEmpty()) return true;
        for (String role : writeRoles) {
            if (req.isUserInRole(role)) return true;
        }
        return false;
    }

    private String resolveTenantId() {
        if (tenantGuard == null || !tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }

    /** Inbound payload — single content field so consumers can extend later. */
    public record TenantMemoryPayload(String content) {}
}
