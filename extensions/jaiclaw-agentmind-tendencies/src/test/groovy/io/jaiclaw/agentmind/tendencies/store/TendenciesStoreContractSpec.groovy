package io.jaiclaw.agentmind.tendencies.store

import io.jaiclaw.core.agent.StaleTendenciesVersionException
import io.jaiclaw.core.agent.TendenciesStoreProvider
import io.jaiclaw.core.model.Tendencies
import io.jaiclaw.core.model.TendenciesScope
import spock.lang.Specification

import java.time.Instant
import java.util.Optional

/**
 * Abstract contract spec every {@link TendenciesStoreProvider} backend
 * must pass. Plan §8 task 3.4 — mirrors {@code TaskStoreContractSpec}.
 *
 * <p>Phase 3a ships this against {@code JsonTendenciesStoreProvider}.
 * Phase 3b (deferred) will add Redis + Postgres-via-Testcontainers;
 * those concrete subclasses extend this spec and provide their own
 * {@link #createStore} factory.
 *
 * <p>The contract pins:
 * <ol>
 *   <li>type() returns the expected backend identifier</li>
 *   <li>save → find round-trips every field (markdown, traits, version,
 *       dialecticPasses, lastDialecticAt)</li>
 *   <li>save on a new key writes at the given version</li>
 *   <li>save with version strictly greater than stored bumps to that
 *       version</li>
 *   <li>save with version equal to or less than stored throws
 *       StaleTendenciesVersionException</li>
 *   <li>findTendencies on a missing key returns empty</li>
 *   <li>delete is idempotent on missing keys</li>
 *   <li>Cross-tenant and cross-user reads return empty (isolation)</li>
 *   <li>findActiveUsers returns USER-scope records within the cutoff
 *       window, excluding records older than the cutoff</li>
 *   <li>Scope dispatch: USER and TENANT keys do not collide</li>
 * </ol>
 */
abstract class TendenciesStoreContractSpec extends Specification {

    abstract TendenciesStoreProvider createStore()

    abstract String expectedType()

    def "type() returns the expected backend identifier"() {
        expect:
        createStore().type() == expectedType()
    }

    // ---------- round-trip ----------

    def "save then find round-trips every field"() {
        given:
        TendenciesStoreProvider store = createStore()
        Tendencies original = new Tendencies(TendenciesScope.USER, "acme", "u-1",
                "# Profile\nLikes brevity.",
                [prefers_brevity: "true", tech_leaning: "high"],
                Instant.now(), Instant.now(), 3L, 0L)

        when:
        store.saveTendencies(original)
        Optional<Tendencies> loaded = store.findTendencies("acme", TendenciesScope.USER, "u-1")

        then:
        loaded.present
        Tendencies l = loaded.get()
        l.scope() == TendenciesScope.USER
        l.tenantId() == "acme"
        l.canonicalUserId() == "u-1"
        l.peerCardMarkdown().contains("Likes brevity")
        l.traits() == [prefers_brevity: "true", tech_leaning: "high"]
        l.dialecticPasses() == 3L
        l.lastDialecticAt() != null
    }

    def "find on a missing key returns empty"() {
        expect:
        createStore().findTendencies("acme", TendenciesScope.USER, "ghost").empty
    }

    // ---------- optimistic CAS ----------

    def "first write at version 0 succeeds"() {
        given:
        TendenciesStoreProvider store = createStore()

        when:
        Tendencies out = store.saveTendencies(Tendencies.forUser("acme", "u-1", "v0", [:]))

        then:
        out.version() == 0L
    }

    def "writing a strictly newer version replaces content"() {
        given:
        TendenciesStoreProvider store = createStore()
        Tendencies v0 = store.saveTendencies(Tendencies.forUser("acme", "u-1", "v0", [:]))

        when:
        Tendencies v1 = store.saveTendencies(v0.withDialecticResult("v1", [a: "1"]))

        then:
        v1.version() == 1L
        store.findTendencies("acme", TendenciesScope.USER, "u-1").get().peerCardMarkdown() == "v1"
    }

    def "writing the same version is rejected as stale"() {
        given:
        TendenciesStoreProvider store = createStore()
        Tendencies v0 = store.saveTendencies(Tendencies.forUser("acme", "u-1", "v0", [:]))

        when:
        store.saveTendencies(v0)

        then:
        thrown(StaleTendenciesVersionException)
    }

    def "writing an older version is rejected as stale"() {
        given:
        TendenciesStoreProvider store = createStore()
        Tendencies v0 = store.saveTendencies(Tendencies.forUser("acme", "u-1", "v0", [:]))
        store.saveTendencies(v0.withDialecticResult("v1", [:]))

        when:
        store.saveTendencies(v0) // forged retry at v0

        then:
        thrown(StaleTendenciesVersionException)
    }

    // ---------- delete ----------

    def "delete removes the stored record"() {
        given:
        TendenciesStoreProvider store = createStore()
        store.saveTendencies(Tendencies.forUser("acme", "u-1", "x", [:]))

        when:
        store.deleteTendencies("acme", TendenciesScope.USER, "u-1")

        then:
        store.findTendencies("acme", TendenciesScope.USER, "u-1").empty
    }

    def "delete on a missing key is a no-op"() {
        when:
        createStore().deleteTendencies("acme", TendenciesScope.USER, "ghost")

        then:
        notThrown(Exception)
    }

    // ---------- isolation ----------

    def "USER-scope reads do not leak across tenants"() {
        given:
        TendenciesStoreProvider store = createStore()
        store.saveTendencies(Tendencies.forUser("tenantA", "shared-u", "A", [:]))
        store.saveTendencies(Tendencies.forUser("tenantB", "shared-u", "B", [:]))

        expect:
        store.findTendencies("tenantA", TendenciesScope.USER, "shared-u").get().peerCardMarkdown() == "A"
        store.findTendencies("tenantB", TendenciesScope.USER, "shared-u").get().peerCardMarkdown() == "B"
    }

    def "USER-scope reads do not leak across users within a tenant"() {
        given:
        TendenciesStoreProvider store = createStore()
        store.saveTendencies(Tendencies.forUser("acme", "u-A", "A", [:]))
        store.saveTendencies(Tendencies.forUser("acme", "u-B", "B", [:]))

        expect:
        store.findTendencies("acme", TendenciesScope.USER, "u-A").get().peerCardMarkdown() == "A"
        store.findTendencies("acme", TendenciesScope.USER, "u-B").get().peerCardMarkdown() == "B"
    }

    def "TENANT and USER scopes do not collide on key dispatch"() {
        given:
        TendenciesStoreProvider store = createStore()
        store.saveTendencies(Tendencies.forTenant("acme", "tenant md", [:]))
        store.saveTendencies(Tendencies.forUser("acme", "u-1", "user md", [:]))

        expect:
        store.findTendencies("acme", TendenciesScope.TENANT, null).get().peerCardMarkdown() == "tenant md"
        store.findTendencies("acme", TendenciesScope.USER, "u-1").get().peerCardMarkdown() == "user md"
    }

    // ---------- findActiveUsers ----------

    def "findActiveUsers returns USER records whose updatedAt is within the window"() {
        given:
        TendenciesStoreProvider store = createStore()
        store.saveTendencies(Tendencies.forUser("acme", "u-1", "fresh", [:]))
        store.saveTendencies(Tendencies.forUser("acme", "u-2", "fresh", [:]))

        when:
        long now = Instant.now().toEpochMilli()
        Collection<Tendencies> active = store.findActiveUsers("acme", now - 60_000)

        then:
        active.size() == 2
        active*.canonicalUserId() as Set == ["u-1", "u-2"] as Set
    }

    def "findActiveUsers excludes records older than the cutoff"() {
        given:
        TendenciesStoreProvider store = createStore()
        store.saveTendencies(Tendencies.forUser("acme", "u-1", "fresh", [:]))

        when:
        // Cutoff is in the future — no records should qualify.
        long future = Instant.now().toEpochMilli() + 60_000
        Collection<Tendencies> active = store.findActiveUsers("acme", future)

        then:
        active.empty
    }

    def "findActiveUsers returns empty for unknown tenant"() {
        expect:
        createStore().findActiveUsers("ghost", 0L).empty
    }
}
