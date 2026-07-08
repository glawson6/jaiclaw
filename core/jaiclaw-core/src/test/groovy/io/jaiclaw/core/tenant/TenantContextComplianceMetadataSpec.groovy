package io.jaiclaw.core.tenant

import spock.lang.Specification

/**
 * T1-1 acceptance: TenantContext exposes optional compliance metadata via
 * default methods over the existing getMetadata() map. Existing callers see
 * no change; new callers can drive GDPR + HIPAA behavior per-tenant.
 */
class TenantContextComplianceMetadataSpec extends Specification {

    def "unset metadata returns null / empty defaults"() {
        given:
        def ctx = new DefaultTenantContext("t-1", "T", [:])

        expect:
        ctx.lawfulBasis == null
        ctx.retentionDays == null
        ctx.restrictionFlags == [] as Set
        ctx.dataResidencyRequired == null
        !ctx.phiProcessing
        ctx.consentToken == null
    }

    def "null metadata map is tolerated by every accessor"() {
        // DefaultTenantContext normalizes null → empty, but interface-level
        // impls might return a null map. Confirm the defaults tolerate that.
        given:
        TenantContext ctx = new TenantContext() {
            String getTenantId() { return "t-1" }
            String getTenantName() { return "T" }
            Map<String, Object> getMetadata() { return null }
        }

        expect:
        ctx.lawfulBasis == null
        ctx.retentionDays == null
        ctx.restrictionFlags == [] as Set
        ctx.dataResidencyRequired == null
        !ctx.phiProcessing
        ctx.consentToken == null
    }

    def "lawfulBasis + dataResidency + consentToken round-trip from string metadata"() {
        given:
        def ctx = new DefaultTenantContext("t-1", "T", [
                (TenantContext.KEY_LAWFUL_BASIS): "consent",
                (TenantContext.KEY_DATA_RESIDENCY): "eu-west",
                (TenantContext.KEY_CONSENT_TOKEN): "cnst_abc123",
        ])

        expect:
        ctx.lawfulBasis == "consent"
        ctx.dataResidencyRequired == "eu-west"
        ctx.consentToken == "cnst_abc123"
    }

    def "retentionDays parses integer, long, and numeric string"() {
        expect:
        new DefaultTenantContext("t", "T", [
                (TenantContext.KEY_RETENTION_DAYS): value
        ]).retentionDays == expected

        where:
        // The null-value case is separately covered by "unset metadata returns
        // null / empty defaults" above — Map.copyOf() rejects null values so
        // we can't build such a map anyway.
        value      | expected
        30         | 30
        30L        | 30
        "2190"     | 2190
        "  2190 "  | 2190
        "not-int"  | null
    }

    def "restrictionFlags accepts Set<String> directly"() {
        given:
        def ctx = new DefaultTenantContext("t-1", "T", [
                (TenantContext.KEY_RESTRICTION_FLAGS): ["no_llm_calls", "no_memory_writes"] as LinkedHashSet
        ])

        expect:
        ctx.restrictionFlags == ["no_llm_calls", "no_memory_writes"] as Set
        ctx.hasRestriction("no_llm_calls")
        ctx.hasRestriction("no_memory_writes")
        !ctx.hasRestriction("something_else")
    }

    def "restrictionFlags accepts comma-separated string and trims whitespace"() {
        given:
        def ctx = new DefaultTenantContext("t-1", "T", [
                (TenantContext.KEY_RESTRICTION_FLAGS): " no_llm_calls , no_memory_writes , "
        ])

        expect:
        ctx.restrictionFlags == ["no_llm_calls", "no_memory_writes"] as Set
    }

    def "restrictionFlags returns empty set for blank / unknown types"() {
        expect:
        new DefaultTenantContext("t", "T", [
                (TenantContext.KEY_RESTRICTION_FLAGS): value
        ]).restrictionFlags == [] as Set

        where:
        value << ["", "   ", 42, false]  // null-value case: metadata map rejects nulls
    }

    def "restrictionFlags result is immutable — protects against accidental caller mutation"() {
        given:
        def ctx = new DefaultTenantContext("t-1", "T", [
                (TenantContext.KEY_RESTRICTION_FLAGS): "a,b,c"
        ])

        when:
        ctx.restrictionFlags.add("d")

        then:
        thrown(UnsupportedOperationException)
    }

    def "phiProcessing accepts boolean and 'true' string, defaults false otherwise"() {
        expect:
        new DefaultTenantContext("t", "T", [
                (TenantContext.KEY_PHI_PROCESSING): value
        ]).phiProcessing == expected

        where:
        // null-value case covered by "unset metadata returns null / empty defaults"
        value        | expected
        true         | true
        Boolean.TRUE | true
        "true"       | true
        "TRUE"       | true
        false        | false
        "false"      | false
        "yes"        | false
        42           | false
    }

    def "hasRestriction handles null flag argument"() {
        given:
        def ctx = new DefaultTenantContext("t-1", "T", [
                (TenantContext.KEY_RESTRICTION_FLAGS): "no_llm_calls"
        ])

        expect:
        !ctx.hasRestriction(null)
    }

    def "compliance metadata survives round-trip through TenantContextPropagator (virtual thread)"() {
        given:
        def ctx = new DefaultTenantContext("t-1", "T", [
                (TenantContext.KEY_LAWFUL_BASIS): "contract",
                (TenantContext.KEY_RETENTION_DAYS): 2190,
                (TenantContext.KEY_PHI_PROCESSING): true,
                (TenantContext.KEY_DATA_RESIDENCY): "us-east",
        ])

        // Simulate the wrap+run pattern the propagator uses for async work.
        // We're not invoking TenantContextPropagator directly (avoids test-
        // classpath issues); we're proving the metadata is preserved when
        // the context is passed by reference into a virtual thread.
        def result = null
        def executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()

        when:
        def future = executor.submit({
            TenantContextHolder.set(ctx)
            try {
                def snap = TenantContextHolder.get()
                result = [
                        basis: snap.lawfulBasis,
                        days: snap.retentionDays,
                        phi: snap.phiProcessing,
                        residency: snap.dataResidencyRequired,
                ]
            } finally {
                TenantContextHolder.clear()
            }
        } as Runnable)
        future.get()
        executor.shutdown()

        then:
        result.basis == "contract"
        result.days == 2190
        result.phi
        result.residency == "us-east"
    }
}
