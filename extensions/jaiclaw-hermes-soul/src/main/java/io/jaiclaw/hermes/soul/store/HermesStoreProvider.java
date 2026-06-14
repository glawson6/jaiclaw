package io.jaiclaw.hermes.soul.store;

import io.jaiclaw.core.agent.SoulProvider;

/**
 * SPI for hermes storage. Returns scope-aware sub-stores per concept.
 *
 * <p>Phase 1 ships only {@link #soulStore()}. {@code MemoryStore} and
 * {@code TendenciesStore} land in Phase 2 and Phase 3 respectively; the
 * shape is named here so consumers can configure a single provider for the
 * whole hermes surface.
 *
 * <p>The default implementation is {@code JsonHermesStoreProvider} (file
 * storage). Future backends (H2, Postgres, Redis) implement the same SPI
 * and are selected via {@code jaiclaw.hermes.storage.type}.
 *
 * <p>Plan §5 task 1.5 — SPI shape mirrors
 * {@code io.jaiclaw.tasks.persistence.TaskStoreProvider}.
 */
public interface HermesStoreProvider {

    /**
     * Identifies the backend (e.g. {@code "json"}, {@code "h2"}, {@code "redis"}).
     * Used by autoconfig to pick the right provider against the
     * {@code jaiclaw.hermes.storage.type} property.
     */
    String type();

    /**
     * Soul sub-store. Implementations MUST handle both {@code SoulScope.TENANT}
     * and {@code SoulScope.AGENT}.
     */
    SoulProvider soulStore();
}
