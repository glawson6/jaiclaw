package io.jaiclaw.core.tenant

import spock.lang.Specification

class TenantGuardStorageKeySpec extends Specification {

    def "resolveStorageKey in SINGLE mode uses the configured defaultTenantId"() {
        given:
        TenantProperties props = new TenantProperties(TenantMode.SINGLE, "my-tenant-id-1234", false)
        TenantGuard guard = new TenantGuard(props)

        expect:
        guard.resolveStorageKey("artifact-1") == "my-tenant-id-1234:artifact-1"
        guard.resolveStoragePrefix() == "my-tenant-id-1234"
    }

    def "resolveStorageKey in SINGLE mode falls back to the literal 'default' placeholder"() {
        given:
        TenantProperties props = new TenantProperties(TenantMode.SINGLE, null, false)
        TenantGuard guard = new TenantGuard(props)

        expect:
        guard.resolveStorageKey("artifact-1") == "default:artifact-1"
        guard.resolveStoragePrefix() == "default"
    }

    def "resolveStorageKey in MULTI mode uses the current thread's tenantId"() {
        given:
        TenantProperties props = new TenantProperties(TenantMode.MULTI, "ignored", false)
        TenantGuard guard = new TenantGuard(props)
        TenantContextHolder.set(new DefaultTenantContext("tenant-a", "tenant-a"))

        expect:
        guard.resolveStorageKey("artifact-1") == "tenant-a:artifact-1"

        cleanup:
        TenantContextHolder.clear()
    }

    def "resolveStorageKey in MULTI mode throws when no context is set"() {
        given:
        TenantProperties props = new TenantProperties(TenantMode.MULTI, null, false)
        TenantGuard guard = new TenantGuard(props)

        when:
        guard.resolveStorageKey("artifact-1")

        then:
        IllegalStateException ex = thrown()
        ex.message.contains("Multi-tenant")
    }

    def "resolveStorageKey rejects null businessId"() {
        given:
        TenantGuard guard = new TenantGuard(TenantProperties.DEFAULT)

        when:
        guard.resolveStorageKey(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "two tenants get distinct prefixes in MULTI mode"() {
        given:
        TenantProperties props = new TenantProperties(TenantMode.MULTI, "ignored", false)
        TenantGuard guard = new TenantGuard(props)

        when:
        TenantContextHolder.set(new DefaultTenantContext("tenant-a", "tenant-a"))
        String keyA = guard.resolveStorageKey("artifact-1")
        TenantContextHolder.set(new DefaultTenantContext("tenant-b", "tenant-b"))
        String keyB = guard.resolveStorageKey("artifact-1")

        then:
        keyA == "tenant-a:artifact-1"
        keyB == "tenant-b:artifact-1"
        keyA != keyB

        cleanup:
        TenantContextHolder.clear()
    }

    def "legacy resolveTenantPrefix retains its empty-string SINGLE-mode behaviour"() {
        given:
        TenantGuard guard = new TenantGuard(TenantProperties.DEFAULT)

        expect:
        guard.resolveTenantPrefix() == ""
    }
}
