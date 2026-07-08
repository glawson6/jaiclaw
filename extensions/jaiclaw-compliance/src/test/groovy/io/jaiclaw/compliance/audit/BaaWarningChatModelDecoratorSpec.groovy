package io.jaiclaw.compliance.audit

import io.jaiclaw.config.LlmConfig
import io.jaiclaw.config.ModelsProperties
import io.jaiclaw.config.TenantAgentConfig
import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import spock.lang.Specification

class BaaWarningChatModelDecoratorSpec extends Specification {

    def cleanup() { TenantContextHolder.clear() }

    def "no warning when tenant is not PHI-processing"() {
        given:
        TenantContextHolder.set(phiContext(false))
        def decorator = new BaaWarningChatModelDecorator(new ModelsProperties([:]))

        when:
        decorator.check(configFor("anthropic"))

        then:
        noExceptionThrown()
    }

    def "no warning when provider is BAA-eligible even if PHI-processing"() {
        given:
        TenantContextHolder.set(phiContext(true))
        def decorator = new BaaWarningChatModelDecorator(new ModelsProperties([:]))

        when:
        decorator.check(configFor("bedrock"))

        then: "bedrock is BAA-eligible by default; no warning triggered"
        noExceptionThrown()
    }

    def "warning fires when PHI tenant lands on a non-BAA provider"() {
        given:
        TenantContextHolder.set(phiContext(true))
        def decorator = new BaaWarningChatModelDecorator(new ModelsProperties([:]))

        when:
        decorator.check(configFor("anthropic"))

        then: "check completes without exception even though a WARN is logged"
        noExceptionThrown()
    }

    def "explicit baa-eligible override suppresses the warning"() {
        given:
        TenantContextHolder.set(phiContext(true))
        def props = new ModelsProperties([
                "anthropic": ModelsProperties.ModelProviderConfig.builder()
                        .baseUrl("https://api.anthropic.com").apiKey("k").api("sdk")
                        .baaEligible(true).build()
        ])
        def decorator = new BaaWarningChatModelDecorator(props)

        when:
        decorator.check(configFor("anthropic"))

        then:
        noExceptionThrown()
    }

    def "null / empty config is tolerated"() {
        given:
        def decorator = new BaaWarningChatModelDecorator(new ModelsProperties([:]))

        expect:
        decorator.check(null) == null
    }

    private TenantContext phiContext(boolean phi) {
        Map<String, Object> md = phi ? [(TenantContext.KEY_PHI_PROCESSING): true] : [:]
        return new DefaultTenantContext("acme", "acme", md)
    }

    private TenantAgentConfig configFor(String provider) {
        return TenantAgentConfig.builder()
                .tenantId("acme").agentId("agent1").name("acme-agent")
                .llm(new LlmConfig(provider, "claude-sonnet-4-5", [], null, 0.5, 4096, 60))
                .build()
    }
}
