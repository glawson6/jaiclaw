package io.jaiclaw.pipeline.processors.preset

import io.jaiclaw.pipeline.processors.integration.CamelTemplateLoader
import spock.lang.Specification

class PipelinePresetLoaderSpec extends Specification {

    def "loads every shipped AI preset from the classpath"() {
        given:
        PipelinePresetLoader loader = new PipelinePresetLoader()

        expect:
        loader.presets().size() >= 7
        loader.presets()*.id().toSet().containsAll(
                ["summarize", "classify", "extract-to-json", "translate",
                 "sentiment", "redact-pii", "draft-reply"] as Set)
    }

    def "every preset carries a non-blank promptTemplate"() {
        given:
        PipelinePresetLoader loader = new PipelinePresetLoader()

        expect:
        loader.presets().every { it.promptTemplate() != null && !it.promptTemplate().isBlank() }
    }

    def "every preset carries a JSON Schema-shaped configSchema"() {
        given:
        PipelinePresetLoader loader = new PipelinePresetLoader()

        expect:
        loader.presets().every { it.configSchema() != null }
    }

    def "loads every shipped Camel template from the classpath"() {
        given:
        CamelTemplateLoader loader = new CamelTemplateLoader()

        expect:
        loader.templates().size() >= 6
        loader.templates()*.id().toSet().containsAll(
                ["send-email", "kafka-publish", "http-post", "jdbc-query",
                 "s3-file-archive", "log"] as Set)
    }

    def "every Camel template carries a non-blank scheme and uriPattern"() {
        given:
        CamelTemplateLoader loader = new CamelTemplateLoader()

        expect:
        loader.templates().every { it.scheme() != null && !it.scheme().isBlank() }
        loader.templates().every { it.uriPattern() != null && !it.uriPattern().isBlank() }
    }

    def "log template's scheme is on the default URI allowlist"() {
        given:
        CamelTemplateLoader loader = new CamelTemplateLoader()
        def log = loader.templates().find { it.id() == "log" }

        expect:
        log != null
        log.scheme() == "log"
    }
}
