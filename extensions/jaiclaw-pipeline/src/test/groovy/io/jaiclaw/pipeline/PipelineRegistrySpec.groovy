package io.jaiclaw.pipeline

import spock.lang.Specification

class PipelineRegistrySpec extends Specification {

    PipelineRegistry registry = new PipelineRegistry()

    def "register and get pipeline"() {
        given:
        PipelineDefinition def1 = new PipelineDefinition(
                "pipe-1", "Pipeline 1", null, List.of(), true,
                null, null, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "bean1", null, null, null, null, null, null)],
                null, null
        )

        when:
        registry.register(def1)

        then:
        registry.get("pipe-1") == def1
        registry.contains("pipe-1")
        registry.size() == 1
    }

    def "getAll returns all registered pipelines"() {
        given:
        PipelineDefinition def1 = makePipeline("p1", List.of())
        PipelineDefinition def2 = makePipeline("p2", List.of())

        when:
        registry.register(def1)
        registry.register(def2)

        then:
        registry.getAll().size() == 2
    }

    def "get returns null for unknown ID"() {
        expect:
        registry.get("nonexistent") == null
    }

    def "getForTenant filters by tenant"() {
        given:
        PipelineDefinition global = makePipeline("global", List.of())
        PipelineDefinition tenant1 = makePipeline("t1", List.of("tenant-a"))
        PipelineDefinition tenant2 = makePipeline("t2", List.of("tenant-b"))

        registry.register(global)
        registry.register(tenant1)
        registry.register(tenant2)

        when:
        List<PipelineDefinition> forTenantA = registry.getForTenant("tenant-a")

        then:
        forTenantA.size() == 2
        forTenantA.any { it.id() == "global" }
        forTenantA.any { it.id() == "t1" }
        !forTenantA.any { it.id() == "t2" }
    }

    def "code-defined pipeline overrides YAML-defined with same ID"() {
        given:
        PipelineDefinition yaml = makePipeline("same-id", List.of())
        PipelineDefinition code = new PipelineDefinition(
                "same-id", "Code Pipeline", "from code", List.of(), true,
                null, null, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "bean2", null, null, null, null, null, null)],
                null, null
        )

        when:
        registry.register(yaml)
        registry.register(code)

        then:
        registry.get("same-id").name() == "Code Pipeline"
        registry.size() == 1
    }

    private PipelineDefinition makePipeline(String id, List<String> tenantIds) {
        return new PipelineDefinition(
                id, id, null, tenantIds, true,
                null, null, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "bean1", null, null, null, null, null, null)],
                null, null
        )
    }
}
