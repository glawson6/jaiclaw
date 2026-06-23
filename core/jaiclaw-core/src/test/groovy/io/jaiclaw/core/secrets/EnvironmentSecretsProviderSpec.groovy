package io.jaiclaw.core.secrets

import spock.lang.Specification

class EnvironmentSecretsProviderSpec extends Specification {

    def "looks up env var using the default mapper"() {
        given:
        EnvironmentSecretsProvider provider = new EnvironmentSecretsProvider(
                [ANTHROPIC_API_KEY: "sk-abc"])

        expect:
        provider.get("anthropic-api-key").get() == "sk-abc"
        provider.get("anthropic.api.key").get() == "sk-abc"
        provider.get("ANTHROPIC_API_KEY").get() == "sk-abc"
    }

    def "returns empty for missing keys"() {
        given:
        EnvironmentSecretsProvider provider = new EnvironmentSecretsProvider([:])

        expect:
        provider.get("anything").isEmpty()
    }

    def "getAll strips the mapped prefix"() {
        given:
        EnvironmentSecretsProvider provider = new EnvironmentSecretsProvider([
                TENANT_ACME_ANTHROPIC_KEY: "sk-acme",
                TENANT_ACME_OPENAI_KEY:    "sk-acme-oai",
                TENANT_OTHER_ANTHROPIC_KEY: "sk-other",
                UNRELATED:                 "x",
        ])

        when:
        Map<String, String> acme = provider.getAll("tenant-acme-")

        then:
        acme.size() == 2
        acme.ANTHROPIC_KEY == "sk-acme"
        acme.OPENAI_KEY == "sk-acme-oai"
    }

    def "name() returns 'env'"() {
        expect:
        new EnvironmentSecretsProvider([:]).name() == "env"
    }

    def "custom mapper is honored"() {
        given:
        EnvironmentSecretsProvider provider = new EnvironmentSecretsProvider(
                [my_key: "v"],
                { String s -> s.toLowerCase() })

        expect:
        provider.get("MY_KEY").get() == "v"
    }
}
