package io.jaiclaw.core.tenant

import spock.lang.Specification
import spock.lang.Unroll

class TenantPropertiesStrictSpec extends Specification {

    @Unroll
    def "strict mode rejects weak defaultTenantId '#value'"() {
        when:
        new TenantProperties(TenantMode.SINGLE, value, true)

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains(value)
        ex.message.contains("strict-default-tenant-id")

        where:
        value << ["default", "tenant", "production", "abc", "short", "a", ""]
    }

    @Unroll
    def "strict mode accepts high-entropy defaultTenantId '#value'"() {
        when:
        TenantProperties props = new TenantProperties(TenantMode.SINGLE, value, true)

        then:
        noExceptionThrown()
        props.defaultTenantId() == value

        where:
        value << [
                "7f4e1f3a-9b2c-4a8e-bf76-a31c4d7e8f01",   // UUID with hyphens
                "ACME-prod-2026-tenant-007",                // mixed-case with digits + hyphens
                "Aab123456789abcd",                         // 16 chars, mixed
                "tenant-prod-east-1-fleet-7"                // hyphens + digits
        ]
    }

    def "strict mode rejects all-lowercase values even when long"() {
        when:
        new TenantProperties(TenantMode.SINGLE, "supercalifragilistic", true)

        then:
        // 20 chars but all lowercase: still rejected by the `[a-z]+` clause.
        thrown(IllegalArgumentException)
    }

    def "non-strict mode allows weak values without throwing"() {
        when:
        TenantProperties props = new TenantProperties(TenantMode.SINGLE, "default", false)

        then:
        noExceptionThrown()
        props.defaultTenantId() == "default"
        props.isUsingPlaceholderDefaultTenantId()
    }

    def "isUsingPlaceholderDefaultTenantId returns false when overridden"() {
        given:
        TenantProperties props = new TenantProperties(TenantMode.SINGLE, "Aab123456789abcd", false)

        expect:
        !props.isUsingPlaceholderDefaultTenantId()
    }

    def "legacy 2-arg constructor still works and defaults strict to false"() {
        when:
        TenantProperties props = new TenantProperties(TenantMode.SINGLE, "default")

        then:
        noExceptionThrown()
        !props.strictDefaultTenantId()
    }

    def "isWeak rejects empty / short / all-lowercase"() {
        expect:
        TenantProperties.isWeak("")
        TenantProperties.isWeak("a")
        TenantProperties.isWeak("default")
        TenantProperties.isWeak("alllowercaseword")  // 16 chars but all lowercase
        TenantProperties.isWeak(null)
    }

    def "isWeak accepts values with digits or non-lowercase chars and length >= 16"() {
        expect:
        !TenantProperties.isWeak("Aab123456789abcd")
        !TenantProperties.isWeak("7f4e1f3a-9b2c-4a8e-bf76-a31c4d7e8f01")
    }
}
