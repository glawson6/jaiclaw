package io.jaiclaw.pipeline.authoring

import io.jaiclaw.core.tenant.TenantGuard
import io.jaiclaw.core.tenant.TenantProperties
import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.OutputDefinition
import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineSecurityProperties
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class JsonFilePipelineDraftStoreSpec extends Specification {

    @TempDir
    Path tempDir

    TenantGuard tenantGuard = new TenantGuard(TenantProperties.DEFAULT)  // SINGLE mode
    JsonFilePipelineDraftStore store

    def setup() {
        store = new JsonFilePipelineDraftStore(tempDir, tenantGuard)
    }

    private static PipelineDefinition defOf(String id) {
        return new PipelineDefinition(
                id, id, null, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [new StageDefinition("s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                null)
    }

    private static PipelineDraft draftOf(String id, long revision) {
        return new PipelineDraft(id, revision, defOf(id), null,
                PipelineDraft.Status.DRAFT, PipelineDraft.Origin.STUDIO, null)
    }

    def "save persists then find round-trips"() {
        when:
        store.save(draftOf("p1", 1))

        then:
        store.find("p1").isPresent()
        store.find("p1").get().definition().id() == "p1"
        store.find("p1").get().revision() == 1
    }

    def "save on an existing id bumps revision"() {
        given:
        store.save(draftOf("p1", 1))

        when:
        PipelineDraft next = store.save(draftOf("p1", 1))

        then:
        next.revision() == 2
        store.find("p1").get().revision() == 2
    }

    def "save with stale revision throws OptimisticLockException"() {
        given:
        store.save(draftOf("p1", 1))
        store.save(draftOf("p1", 1))  // bumps to 2

        when: "another caller still holds rev 1"
        store.save(draftOf("p1", 1))

        then:
        PipelineDraftStore.OptimisticLockException e = thrown()
        e.expectedRevision() == 1
        e.actualRevision() == 2
    }

    def "findAll lists every stored draft"() {
        given:
        store.save(draftOf("p1", 1))
        store.save(draftOf("p2", 1))

        expect:
        store.findAll().size() == 2
        store.findAll()*.id().sort() == ["p1", "p2"]
    }

    def "delete removes the file"() {
        given:
        store.save(draftOf("p1", 1))

        when:
        store.delete("p1")

        then:
        !store.find("p1").isPresent()
    }

    def "delete on unknown id is a no-op"() {
        when:
        store.delete("nope")
        store.delete(null)
        store.delete("")

        then:
        noExceptionThrown()
    }

    def "find on blank id returns empty"() {
        expect:
        !store.find(null).isPresent()
        !store.find("").isPresent()
    }

    def "creating the store on a non-existent directory creates it"() {
        given:
        Path fresh = tempDir.resolve("fresh")

        when:
        JsonFilePipelineDraftStore newStore = new JsonFilePipelineDraftStore(fresh, tenantGuard)

        then:
        java.nio.file.Files.isDirectory(fresh)
        newStore.findAll().isEmpty()
    }
}
