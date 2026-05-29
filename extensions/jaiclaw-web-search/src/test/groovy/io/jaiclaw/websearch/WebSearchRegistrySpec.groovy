package io.jaiclaw.websearch

import spock.lang.Specification

class WebSearchRegistrySpec extends Specification {

    def registry = new WebSearchRegistry()

    def "register and retrieve provider"() {
        given:
        def provider = Stub(WebSearchProvider) {
            id() >> "test"
            isConfigured() >> true
        }

        when:
        registry.register(provider)

        then:
        registry.providerIds().contains("test")
    }

    def "activeProvider returns configured provider"() {
        given:
        def provider = Stub(WebSearchProvider) {
            id() >> "test"
            isConfigured() >> true
        }
        registry.register(provider)

        expect:
        registry.activeProvider().isPresent()
        registry.activeProvider().get().id() == "test"
    }

    def "setActiveProvider selects specific provider"() {
        given:
        def p1 = Stub(WebSearchProvider) { id() >> "p1"; isConfigured() >> true }
        def p2 = Stub(WebSearchProvider) { id() >> "p2"; isConfigured() >> true }
        registry.register(p1)
        registry.register(p2)

        when:
        registry.setActiveProvider("p2")

        then:
        registry.activeProvider().get().id() == "p2"
    }

    def "setActiveProvider throws for unknown provider"() {
        when:
        registry.setActiveProvider("unknown")

        then:
        thrown(IllegalArgumentException)
    }
}
