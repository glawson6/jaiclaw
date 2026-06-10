package io.jaiclaw.core.artifact;

import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * In-memory {@link ArtifactStore} backed by a tenant-prefixed {@link ConcurrentHashMap}.
 *
 * <p>Storage keys are constructed via {@link TenantGuard#resolveStorageKey(String)},
 * so two tenants storing artifacts with the same business id never collide.
 * The map is intentionally unscoped at the field level; isolation comes from
 * the key construction inside this class.
 */
public class InMemoryArtifactStore implements ArtifactStore {

    private final ConcurrentHashMap<String, StoredArtifact> store = new ConcurrentHashMap<>();
    private final TenantGuard tenantGuard;

    public InMemoryArtifactStore(TenantGuard tenantGuard) {
        this.tenantGuard = tenantGuard != null ? tenantGuard : new TenantGuard(TenantProperties.DEFAULT);
    }

    /**
     * No-arg constructor preserved for examples / tests that don't pass a
     * {@link TenantGuard}. Falls back to a SINGLE-mode guard with the
     * placeholder default tenant id.
     */
    public InMemoryArtifactStore() {
        this(new TenantGuard(TenantProperties.DEFAULT));
    }

    private String key(String businessId) {
        return tenantGuard.resolveStorageKey(businessId);
    }

    @Override
    public void save(StoredArtifact artifact) {
        store.put(key(artifact.id()), artifact);
    }

    @Override
    public Optional<StoredArtifact> findById(String id) {
        return Optional.ofNullable(store.get(key(id)));
    }

    @Override
    public void updateStatus(String id, ArtifactStatus status, String message) {
        store.computeIfPresent(key(id), (k, existing) -> new StoredArtifact(
                existing.id(), existing.data(), existing.mimeType(), existing.filename(),
                status, message, existing.createdAt(), existing.metadata()));
    }

    @Override
    public void delete(String id) {
        store.remove(key(id));
    }

    /**
     * Stream every artifact owned by the current tenant. Used by the
     * regression-guard spec and any consumer that needs to enumerate.
     */
    public Stream<StoredArtifact> findAllForCurrentTenant() {
        String prefix = tenantGuard.resolveStoragePrefix() + ":";
        return store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(java.util.Map.Entry::getValue);
    }
}
