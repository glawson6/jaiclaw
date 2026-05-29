package io.jaiclaw.modelcatalog

import spock.lang.Specification

class ModelCatalogSpec extends Specification {

    def catalog = new ModelCatalog()

    def "register and resolve by id"() {
        given:
        def entry = new ModelEntry("test-model", "provider", "Test Model", 100000, CostTier.LOW,
                Set.of(ModelCapability.CHAT), List.of())
        catalog.register(entry)

        expect:
        catalog.resolve("test-model").isPresent()
        catalog.resolve("test-model").get().displayName() == "Test Model"
    }

    def "resolve by alias"() {
        given:
        def entry = new ModelEntry("gpt-4o", "openai", "GPT-4o", 128000, CostTier.MEDIUM,
                Set.of(ModelCapability.CHAT), List.of("gpt4o", "4o"))
        catalog.register(entry)

        expect:
        catalog.resolve("gpt4o").isPresent()
        catalog.resolve("4o").isPresent()
        catalog.resolve("gpt4o").get().id() == "gpt-4o"
    }

    def "resolve returns empty for unknown id"() {
        expect:
        catalog.resolve("nonexistent").isEmpty()
    }

    def "findByCapabilities filters correctly"() {
        given:
        catalog.register(new ModelEntry("m1", "p", "M1", 100000, CostTier.LOW,
                Set.of(ModelCapability.CHAT, ModelCapability.VISION), List.of()))
        catalog.register(new ModelEntry("m2", "p", "M2", 50000, CostTier.LOW,
                Set.of(ModelCapability.CHAT), List.of()))

        when:
        def results = catalog.findByCapabilities(Set.of(ModelCapability.CHAT, ModelCapability.VISION))

        then:
        results.size() == 1
        results[0].id() == "m1"
    }

    def "findByCostTier filters correctly"() {
        given:
        catalog.register(new ModelEntry("m1", "p", "M1", 100000, CostTier.HIGH,
                Set.of(), List.of()))
        catalog.register(new ModelEntry("m2", "p", "M2", 50000, CostTier.LOW,
                Set.of(), List.of()))

        expect:
        catalog.findByCostTier(CostTier.LOW).size() == 1
        catalog.findByCostTier(CostTier.HIGH).size() == 1
    }

    def "findByProvider filters correctly"() {
        given:
        catalog.register(new ModelEntry("m1", "anthropic", "M1", 100000, CostTier.HIGH,
                Set.of(), List.of()))
        catalog.register(new ModelEntry("m2", "openai", "M2", 50000, CostTier.LOW,
                Set.of(), List.of()))

        expect:
        catalog.findByProvider("anthropic").size() == 1
        catalog.findByProvider("openai").size() == 1
    }
}
