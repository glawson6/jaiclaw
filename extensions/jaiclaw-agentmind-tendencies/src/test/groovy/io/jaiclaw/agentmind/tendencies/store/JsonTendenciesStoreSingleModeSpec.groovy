package io.jaiclaw.agentmind.tendencies.store

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.core.model.Tendencies
import io.jaiclaw.core.model.TendenciesScope
import io.jaiclaw.core.tenant.TenantGuard
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * SINGLE-mode JSON-specific path assertions. The cross-tenant isolation
 * contract is exercised in MULTI mode by
 * {@link JsonTendenciesStoreContractSpec}; this spec verifies that
 * SINGLE-mode collapses the tenant prefix correctly.
 */
class JsonTendenciesStoreSingleModeSpec extends Specification {

    @TempDir
    Path tmp

    TenantGuard singleTenant = Mock() { isMultiTenant() >> false }
    ObjectMapper mapper = new ObjectMapper()

    JsonTendenciesStoreProvider store() {
        new JsonTendenciesStoreProvider(tmp, singleTenant, mapper)
    }

    def "SINGLE-mode USER Tendencies lands under root/users/{userKey}/TENDENCIES.json"() {
        when:
        store().saveTendencies(Tendencies.forUser("default", "u-1", "x", [:]))

        then:
        Files.exists(tmp.resolve("users").resolve("u-1").resolve("TENDENCIES.json"))
    }

    def "SINGLE-mode TENANT Tendencies lands at root/TENANT-TENDENCIES.json"() {
        when:
        store().saveTendencies(Tendencies.forTenant("default", "x", [:]))

        then:
        Files.exists(tmp.resolve("TENANT-TENDENCIES.json"))
    }

    def "SINGLE-mode round-trip works"() {
        given:
        JsonTendenciesStoreProvider s = store()
        s.saveTendencies(Tendencies.forUser("default", "u-1", "v0 markdown", [a: "1"]))

        when:
        Optional<Tendencies> loaded = s.findTendencies("default", TendenciesScope.USER, "u-1")

        then:
        loaded.present
        loaded.get().peerCardMarkdown() == "v0 markdown"
        loaded.get().traits() == [a: "1"]
    }
}
