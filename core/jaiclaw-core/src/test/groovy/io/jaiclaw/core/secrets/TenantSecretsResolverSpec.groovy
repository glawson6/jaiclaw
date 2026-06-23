package io.jaiclaw.core.secrets

import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantMode
import io.jaiclaw.core.tenant.TenantProperties
import spock.lang.Specification

class TenantSecretsResolverSpec extends Specification {

    def cleanup() {
        TenantContextHolder.clear()
    }

    def "SINGLE mode delegates without tenant prefixing"() {
        given:
        EnvironmentSecretsProvider env = new EnvironmentSecretsProvider([
                ANTHROPIC_API_KEY: "sk-shared",
        ])
        SecretsResolver resolver = new SecretsResolver([env])
        TenantGuard guard = new TenantGuard(
                new TenantProperties(TenantMode.SINGLE, "default", false))
        TenantSecretsResolver tenantResolver = new TenantSecretsResolver(resolver, guard)

        when:
        SecretResolution result = tenantResolver.resolve("anthropic-api-key")

        then:
        result instanceof SecretResolution.Resolved
        ((SecretResolution.Resolved) result).value() == "sk-shared"
    }

    def "MULTI mode prefers tenant-scoped key over bare key"() {
        given:
        EnvironmentSecretsProvider env = new EnvironmentSecretsProvider([
                ANTHROPIC_API_KEY:             "sk-shared",
                ACME_ANTHROPIC_API_KEY:        "sk-acme",  // mapped: ACME:anthropic-api-key
        ])
        // The mapper upper-cases and replaces non-alphanum with _, so
        // "acme:anthropic-api-key" → "ACME_ANTHROPIC_API_KEY".
        SecretsResolver resolver = new SecretsResolver([env])
        TenantGuard guard = new TenantGuard(
                new TenantProperties(TenantMode.MULTI, "default", false))
        TenantSecretsResolver tenantResolver = new TenantSecretsResolver(resolver, guard)
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))

        when:
        SecretResolution result = tenantResolver.resolve("anthropic-api-key")

        then:
        result instanceof SecretResolution.Resolved
        ((SecretResolution.Resolved) result).value() == "sk-acme"
    }

    def "MULTI mode falls back to bare key when no tenant-scoped value"() {
        given:
        EnvironmentSecretsProvider env = new EnvironmentSecretsProvider([
                ANTHROPIC_API_KEY: "sk-shared",
                // no ACME_ANTHROPIC_API_KEY
        ])
        SecretsResolver resolver = new SecretsResolver([env])
        TenantGuard guard = new TenantGuard(
                new TenantProperties(TenantMode.MULTI, "default", false))
        TenantSecretsResolver tenantResolver = new TenantSecretsResolver(resolver, guard)
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))

        when:
        SecretResolution result = tenantResolver.resolve("anthropic-api-key")

        then:
        result instanceof SecretResolution.Resolved
        ((SecretResolution.Resolved) result).value() == "sk-shared"
    }

    def "MULTI mode returns Missing when neither tenant-scoped nor bare key exists"() {
        given:
        EnvironmentSecretsProvider env = new EnvironmentSecretsProvider([:])
        SecretsResolver resolver = new SecretsResolver([env])
        TenantGuard guard = new TenantGuard(
                new TenantProperties(TenantMode.MULTI, "default", false))
        TenantSecretsResolver tenantResolver = new TenantSecretsResolver(resolver, guard)
        TenantContextHolder.set(new DefaultTenantContext("acme", "acme"))

        when:
        SecretResolution result = tenantResolver.resolve("missing")

        then:
        result instanceof SecretResolution.Missing
    }

    def "getValue extracts value or empty"() {
        given:
        EnvironmentSecretsProvider env = new EnvironmentSecretsProvider([KEY: "v"])
        SecretsResolver resolver = new SecretsResolver([env])
        TenantGuard guard = new TenantGuard(
                new TenantProperties(TenantMode.SINGLE, "default", false))
        TenantSecretsResolver tenantResolver = new TenantSecretsResolver(resolver, guard)

        expect:
        tenantResolver.getValue("key").get() == "v"
        tenantResolver.getValue("missing").isEmpty()
    }
}
