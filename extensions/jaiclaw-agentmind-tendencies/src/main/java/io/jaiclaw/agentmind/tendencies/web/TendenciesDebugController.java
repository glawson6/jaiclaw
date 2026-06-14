package io.jaiclaw.agentmind.tendencies.web;

import io.jaiclaw.core.agent.TendenciesStoreProvider;
import io.jaiclaw.core.model.Tendencies;
import io.jaiclaw.core.model.TendenciesScope;
import io.jaiclaw.core.tenant.TenantGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Read-only debug endpoint for USER-scope Tendencies. Gated off by default
 * ({@code jaiclaw.agentmind.tendencies.rest.enabled=false}). Intended for
 * ops use only — production consumers should rely on the
 * MessageReceivedEvent injector to consume Tendencies content, not REST
 * reads, to honour the render-cache invariant.
 *
 * <p>Plan §8 task 3.10.
 */
@RestController
@RequestMapping("/api/agentmind/tendencies")
public class TendenciesDebugController {

    private final TendenciesStoreProvider store;
    private final TenantGuard tenantGuard;

    public TendenciesDebugController(TendenciesStoreProvider store, TenantGuard tenantGuard) {
        this.store = store;
        this.tenantGuard = tenantGuard;
    }

    @GetMapping("/user/{userKey}")
    public ResponseEntity<Tendencies> readUserTendencies(@PathVariable String userKey) {
        Optional<Tendencies> doc = store.findTendencies(
                resolveTenantId(), TendenciesScope.USER, userKey);
        return doc.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private String resolveTenantId() {
        if (tenantGuard == null || !tenantGuard.isMultiTenant()) return "default";
        return tenantGuard.requireTenantIfMulti();
    }
}
