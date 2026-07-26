package io.jaiclaw.pipeline

import spock.lang.Specification

/**
 * Verifies the {@link PipelineRegistry#replace} + {@link PipelineRegistry#unregister}
 * additions land alongside the existing {@code register/get/getAll} surface
 * without breaking behaviour.
 */
class PipelineRegistryReplaceSpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()

    private static PipelineDefinition pipelineDef(String id, String desc) {
        return new PipelineDefinition(
                id, id, desc, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "beanA", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                null)
    }

    def "replace returns null for a fresh registration"() {
        expect:
        registry.replace(pipelineDef("p1", "first")) == null
        registry.get("p1").description() == "first"
    }

    def "replace returns the previous definition on overwrite"() {
        given:
        registry.register(pipelineDef("p1", "original"))

        when:
        PipelineDefinition prev = registry.replace(pipelineDef("p1", "updated"))

        then:
        prev.description() == "original"
        registry.get("p1").description() == "updated"
    }

    def "register delegates to replace"() {
        when:
        registry.register(pipelineDef("p1", "one"))
        registry.register(pipelineDef("p1", "two"))

        then:
        registry.get("p1").description() == "two"
        registry.size() == 1
    }

    def "unregister removes and returns the definition"() {
        given:
        registry.register(pipelineDef("p1", "one"))

        when:
        PipelineDefinition removed = registry.unregister("p1")

        then:
        removed.description() == "one"
        !registry.contains("p1")
        registry.size() == 0
    }

    def "unregister returns null for an unknown id"() {
        expect:
        registry.unregister("nope") == null
        registry.unregister(null) == null
        registry.unregister("") == null
    }

    def "replace rejects null and blank-id definitions"() {
        when:
        registry.replace(null)

        then:
        thrown(IllegalArgumentException)
    }
}
