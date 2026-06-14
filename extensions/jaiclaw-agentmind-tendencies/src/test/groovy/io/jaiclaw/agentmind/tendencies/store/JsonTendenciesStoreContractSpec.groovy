package io.jaiclaw.agentmind.tendencies.store

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.jaiclaw.core.agent.TendenciesStoreProvider
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Plan §8 task 3.4 — exercise the shared
 * {@link TendenciesStoreContractSpec} against
 * {@link JsonTendenciesStoreProvider}.
 *
 * <p>Each spec method gets a fresh @TempDir so the contract methods that
 * assume an empty store at start hold. The TenantGuard mock returns
 * SINGLE-mode so paths flatten under the temp root.
 */
class JsonTendenciesStoreContractSpec extends TendenciesStoreContractSpec {

    @TempDir
    Path tmp

    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())

    TenantGuard multiTenant() {
        // The contract requires cross-tenant isolation, so the JSON backend
        // must prefix paths with {tenantId}. SINGLE-mode behaviour is
        // covered separately by the JsonTendenciesStoreSingleModeSpec.
        TenantGuard g = Mock(TenantGuard)
        g.isMultiTenant() >> true
        return g
    }

    @Override
    TendenciesStoreProvider createStore() {
        return new JsonTendenciesStoreProvider(tmp, multiTenant(), mapper)
    }

    @Override
    String expectedType() { "json" }
}
