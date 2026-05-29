package io.jaiclaw.modelcatalog

import spock.lang.Specification

class ModelCatalogLoaderSpec extends Specification {

    def loader = new ModelCatalogLoader()

    def "loads defaults from classpath"() {
        given:
        def catalog = new ModelCatalog()

        when:
        int count = loader.loadFromClasspath("jaiclaw-model-catalog-defaults.yml", catalog)

        then:
        count == 5
        catalog.size() == 5
    }

    def "resolves Anthropic models from defaults"() {
        given:
        def catalog = new ModelCatalog()
        loader.loadFromClasspath("jaiclaw-model-catalog-defaults.yml", catalog)

        expect:
        catalog.resolve("claude-sonnet-4-5").isPresent()
        catalog.resolve("sonnet").isPresent()
        catalog.resolve("claude-sonnet-4-5").get().provider() == "anthropic"
    }

    def "returns 0 for missing resource"() {
        given:
        def catalog = new ModelCatalog()

        expect:
        loader.loadFromClasspath("nonexistent.yml", catalog) == 0
    }
}
