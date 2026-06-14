package io.jaiclaw.agentmind.soul.web;

import io.jaiclaw.core.agent.SoulProvider;
import io.jaiclaw.core.agent.StaleSoulVersionException;
import io.jaiclaw.core.model.Soul;
import io.jaiclaw.core.model.SoulScope;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.agentmind.soul.AgentMindSoulProperties;
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
 * Operator-only write endpoint for TENANT-scope Soul records. Role-guarded by
 * {@code jaiclaw.agentmind.soul.tenant.write.roles} (default
 * {@code [ADMIN, OPERATOR]}). All endpoints gated by
 * {@code jaiclaw.agentmind.soul.tenant.enabled=true}.
 *
 * <p>Plan §5 task 1.16. Tenant Soul writes never go through the agent tool
 * (see {@link io.jaiclaw.agentmind.soul.tool.SoulAgentTool}) — this controller
 * + the matching MCP tool are the only ways to author the org-wide voice.
 *
 * <p>Role check is performed against the standard servlet
 * {@link HttpServletRequest#isUserInRole(String)} so any auth integration
 * (Spring Security, plain servlet filter, custom JWT) that surfaces roles
 * on the request lights up the guard automatically.
 */
@RestController
@RequestMapping("/api/agentmind/soul/tenant")
public class TenantSoulController {

    private static final Logger log = LoggerFactory.getLogger(TenantSoulController.class);

    private final SoulProvider soulProvider;
    private final TenantGuard tenantGuard;
    private final List<String> writeRoles;

    public TenantSoulController(SoulProvider soulProvider, TenantGuard tenantGuard,
                                AgentMindSoulProperties props) {
        this.soulProvider = soulProvider;
        this.tenantGuard = tenantGuard;
        this.writeRoles = props.tenant().writeRoles();
    }

    @GetMapping
    public ResponseEntity<Soul> read() {
        Optional<Soul> soul = soulProvider.findSoul(resolveTenantId(), SoulScope.TENANT, null);
        return soul.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<?> upsert(@RequestBody TenantSoulPayload payload, HttpServletRequest req) {
        if (!authorized(req)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Unauthorised: caller does not hold a required role. "
                            + "Roles checked: " + writeRoles);
        }
        if (payload == null || payload.markdown() == null) {
            return ResponseEntity.badRequest().body("markdown is required");
        }
        String tenantId = resolveTenantId();
        try {
            Optional<Soul> existing = soulProvider.findSoul(tenantId, SoulScope.TENANT, null);
            long nextVersion = existing.map(s -> s.version() + 1L).orElse(0L);
            Soul toPersist = new Soul(SoulScope.TENANT, tenantId, null,
                    payload.markdown(), java.time.Instant.now(), nextVersion);
            Soul saved = soulProvider.saveSoul(toPersist);
            return ResponseEntity.ok(saved);
        } catch (StaleSoulVersionException e) {
            log.warn("Stale tenant-Soul write for tenant={}: {}", tenantId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<?> delete(HttpServletRequest req) {
        if (!authorized(req)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Unauthorised: caller does not hold a required role.");
        }
        soulProvider.deleteSoul(resolveTenantId(), SoulScope.TENANT, null);
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

    /** Inbound payload — single field so consumers can extend later. */
    public record TenantSoulPayload(String markdown) {}
}
