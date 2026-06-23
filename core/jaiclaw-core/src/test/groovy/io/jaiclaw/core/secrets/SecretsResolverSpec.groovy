package io.jaiclaw.core.secrets

import spock.lang.Specification

class SecretsResolverSpec extends Specification {

    def "returns first resolved value in chain order"() {
        given:
        EnvironmentSecretsProvider env = new EnvironmentSecretsProvider([ANTHROPIC_API_KEY: "sk-env"])
        SecretsProvider fileProvider = Stub(SecretsProvider) {
            get(_) >> Optional.of("sk-file")
            name() >> "file"
        }
        SecretsResolver resolver = new SecretsResolver([env, fileProvider])

        when:
        SecretResolution result = resolver.resolve("anthropic-api-key")

        then:
        result instanceof SecretResolution.Resolved
        ((SecretResolution.Resolved) result).value() == "sk-env"
        ((SecretResolution.Resolved) result).providerName() == "env"
    }

    def "falls through to next provider on Missing"() {
        given:
        SecretsProvider empty = Stub(SecretsProvider) {
            get(_) >> Optional.empty()
            name() >> "first"
        }
        SecretsProvider found = Stub(SecretsProvider) {
            get(_) >> Optional.of("sk-second")
            name() >> "second"
        }
        SecretsResolver resolver = new SecretsResolver([empty, found])

        when:
        SecretResolution result = resolver.resolve("key")

        then:
        result instanceof SecretResolution.Resolved
        ((SecretResolution.Resolved) result).providerName() == "second"
    }

    def "returns Missing when no provider has the key"() {
        given:
        SecretsProvider empty = Stub(SecretsProvider) {
            get(_) >> Optional.empty()
            name() >> "empty"
        }
        SecretsResolver resolver = new SecretsResolver([empty])

        when:
        SecretResolution result = resolver.resolve("absent")

        then:
        result instanceof SecretResolution.Missing
        result.key() == "absent"
    }

    def "CONTINUE mode records first error and tries next provider"() {
        given:
        SecretsProvider broken = Stub(SecretsProvider) {
            get(_) >> { throw new RuntimeException("backing-store down") }
            name() >> "broken"
        }
        SecretsProvider working = Stub(SecretsProvider) {
            get(_) >> Optional.of("sk-fallback")
            name() >> "working"
        }
        SecretsResolver resolver = new SecretsResolver([broken, working], SecretsResolver.OnError.CONTINUE)

        when:
        SecretResolution result = resolver.resolve("key")

        then:
        result instanceof SecretResolution.Resolved
        ((SecretResolution.Resolved) result).providerName() == "working"
    }

    def "CONTINUE mode returns ProviderError when no later provider resolves"() {
        given:
        SecretsProvider broken = Stub(SecretsProvider) {
            get(_) >> { throw new RuntimeException("boom") }
            name() >> "broken"
        }
        SecretsResolver resolver = new SecretsResolver([broken], SecretsResolver.OnError.CONTINUE)

        when:
        SecretResolution result = resolver.resolve("key")

        then:
        result instanceof SecretResolution.ProviderError
        ((SecretResolution.ProviderError) result).providerName() == "broken"
    }

    def "FAIL mode short-circuits on error"() {
        given:
        SecretsProvider broken = Stub(SecretsProvider) {
            get(_) >> { throw new RuntimeException("boom") }
            name() >> "broken"
        }
        SecretsProvider working = Mock(SecretsProvider)
        SecretsResolver resolver = new SecretsResolver([broken, working], SecretsResolver.OnError.FAIL)

        when:
        SecretResolution result = resolver.resolve("key")

        then:
        result instanceof SecretResolution.ProviderError
        0 * working.get(_) // working should not be consulted
    }

    def "getValue extracts value or empty"() {
        given:
        EnvironmentSecretsProvider env = new EnvironmentSecretsProvider([KEY: "value"])
        SecretsResolver resolver = new SecretsResolver([env])

        expect:
        resolver.getValue("key").get() == "value"
        resolver.getValue("missing").isEmpty()
    }

    def "getAll merges results with earlier providers winning"() {
        given:
        SecretsProvider first = Stub(SecretsProvider) {
            getAll(_) >> [a: "from-first", b: "from-first"]
            name() >> "first"
        }
        SecretsProvider second = Stub(SecretsProvider) {
            getAll(_) >> [b: "from-second", c: "from-second"]
            name() >> "second"
        }
        SecretsResolver resolver = new SecretsResolver([first, second])

        when:
        Map<String, String> merged = resolver.getAll("prefix")

        then:
        merged.size() == 3
        merged.a == "from-first"
        merged.b == "from-first" // first wins on conflict
        merged.c == "from-second"
    }

    def "rejects empty chain"() {
        when:
        new SecretsResolver([])

        then:
        thrown(IllegalArgumentException)
    }
}
