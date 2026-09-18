package io.jaiclaw.gateway.tenant

import io.jaiclaw.core.tenant.AuthenticatedTenantSupplier
import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContext
import spock.lang.Specification

/**
 * The replacement for the removed {@code JwtTenantResolver}.
 *
 * <p>The behaviour under test is mostly a negative: the resolver must ignore
 * request attributes entirely, because that is where the pre-1.2.0 vulnerability
 * lived — an unsigned JWT in the Authorization header could establish tenant
 * context.
 */
class SecurityContextTenantResolverSpec extends Specification {

    def "returns the tenant carried by the validated principal"() {
        given:
        TenantContext acme = new DefaultTenantContext("acme", "Acme Corp")
        def resolver = new SecurityContextTenantResolver({ Optional.of(acme) } as AuthenticatedTenantSupplier)

        expect:
        resolver.resolve([:]).get().getTenantId() == "acme"
    }

    def "returns empty when nobody is authenticated"() {
        given:
        def resolver = new SecurityContextTenantResolver({ Optional.empty() } as AuthenticatedTenantSupplier)

        expect:
        resolver.resolve([:]).isEmpty()
    }

    def "ignores an Authorization header entirely"() {
        given: "a forged unsigned JWT claiming a tenant, and no authenticated principal"
        def forged = "e30." + Base64.urlEncoder.withoutPadding()
                .encodeToString('{"tenantId":"victim"}'.bytes) + ".x"
        def resolver = new SecurityContextTenantResolver({ Optional.empty() } as AuthenticatedTenantSupplier)

        when:
        def result = resolver.resolve(["authorization": "Bearer " + forged])

        then: "the header is never parsed — this is the whole point of the class"
        result.isEmpty()
    }

    def "ignores a tenant header too — tenancy comes from the principal only"() {
        given:
        def resolver = new SecurityContextTenantResolver({ Optional.empty() } as AuthenticatedTenantSupplier)

        expect:
        resolver.resolve(["x-tenant-id": "victim"]).isEmpty()
    }

    def "runs before the channel resolver so authentication always wins"() {
        expect:
        new SecurityContextTenantResolver(null).order() < new BotTokenTenantResolver().order()
    }

    def "a null supplier degrades to empty rather than throwing"() {
        expect:
        new SecurityContextTenantResolver(null).resolve([:]).isEmpty()
    }
}
