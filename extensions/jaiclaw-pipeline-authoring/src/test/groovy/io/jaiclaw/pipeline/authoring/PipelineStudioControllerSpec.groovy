package io.jaiclaw.pipeline.authoring

import io.jaiclaw.pipeline.ErrorStrategy
import io.jaiclaw.pipeline.OutputDefinition
import io.jaiclaw.pipeline.OutputType
import io.jaiclaw.pipeline.PipelineDefinition
import io.jaiclaw.pipeline.PipelineSecurityProperties
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import io.jaiclaw.pipeline.TriggerDefinition
import io.jaiclaw.pipeline.TriggerType
import io.jaiclaw.pipeline.validation.PipelineValidator
import io.jaiclaw.pipeline.validation.ValidationReport
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import java.time.Instant

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class PipelineStudioControllerSpec extends Specification {

    PipelineDraftStore store = Mock()
    PipelineCatalogService catalogService = Mock()
    PipelineValidator validator = Mock()

    PipelineStudioController controller = new PipelineStudioController(store, catalogService, validator)
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build()

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

    def "GET /drafts returns the store's list"() {
        given:
        store.findAll() >> [new PipelineDraft("p1", 1, defOf("p1"), null,
                PipelineDraft.Status.DRAFT, PipelineDraft.Origin.STUDIO, Instant.now())]

        when:
        def result = mvc.perform(get("/api/pipeline-studio/drafts"))

        then:
        result.andExpect(status().isOk())
                .andExpect(jsonPath('$[0].id').value("p1"))
                .andExpect(jsonPath('$[0].revision').value(1))
    }

    def "GET /drafts/{id} returns 404 when missing"() {
        given:
        store.find("nope") >> Optional.empty()

        expect:
        mvc.perform(get("/api/pipeline-studio/drafts/nope"))
                .andExpect(status().isNotFound())
    }

    def "POST /drafts creates and returns 201 + ETag"() {
        given:
        store.find("p1") >> Optional.empty()
        store.save(_ as PipelineDraft) >> { PipelineDraft d -> d }

        when:
        def result = mvc.perform(post("/api/pipeline-studio/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"id":"p1","enabled":true,"maxRetries":3,"stages":[{"name":"s1","type":"PROCESSOR","bean":"b"}]}'))

        then:
        result.andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath('$.id').value("p1"))
                .andExpect(jsonPath('$.revision').value(1))
    }

    def "POST /drafts returns 409 when a draft with the same id already exists"() {
        given:
        store.find("p1") >> Optional.of(new PipelineDraft("p1", 1, defOf("p1"), null,
                PipelineDraft.Status.DRAFT, PipelineDraft.Origin.STUDIO, Instant.now()))

        expect:
        mvc.perform(post("/api/pipeline-studio/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"id":"p1","enabled":true,"maxRetries":3,"stages":[{"name":"s1","type":"PROCESSOR","bean":"b"}]}'))
                .andExpect(status().isConflict())
    }

    def "PUT /drafts/{id} returns 409 on revision mismatch"() {
        given:
        store.find("p1") >> Optional.of(new PipelineDraft("p1", 5, defOf("p1"), null,
                PipelineDraft.Status.DRAFT, PipelineDraft.Origin.STUDIO, Instant.now()))
        store.save(_ as PipelineDraft) >> {
            throw new PipelineDraftStore.OptimisticLockException("p1", 3, 5)
        }

        expect:
        mvc.perform(put("/api/pipeline-studio/drafts/p1")
                .header("If-Match", "\"3\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"id":"p1","enabled":true,"maxRetries":3,"stages":[{"name":"s1","type":"PROCESSOR","bean":"b"}]}'))
                .andExpect(status().isConflict())
                .andExpect(jsonPath('$.expected').value(3))
                .andExpect(jsonPath('$.actual').value(5))
    }

    def "DELETE /drafts/{id} returns 204"() {
        expect:
        mvc.perform(delete("/api/pipeline-studio/drafts/p1"))
                .andExpect(status().isNoContent())
    }

    def "GET /catalog returns catalog map"() {
        given:
        catalogService.catalog() >> [triggerTypes: ["MANUAL"], stageTypes: ["PROCESSOR"]]

        expect:
        mvc.perform(get("/api/pipeline-studio/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.triggerTypes[0]').value("MANUAL"))
    }

    def "POST /validate returns the validator's report shape"() {
        given:
        ValidationReport.Builder b = new ValidationReport.Builder()
        validator.validate(_ as PipelineDefinition) >> b.build()

        expect:
        mvc.perform(post("/api/pipeline-studio/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"id":"p1","enabled":true,"maxRetries":3,"stages":[{"name":"s1","type":"PROCESSOR","bean":"b"}]}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.hasErrors').value(false))
                .andExpect(jsonPath('$.errors').isArray())
    }

    def "POST /validate with blank id returns 200 + ID_BLANK error (not a 500)"() {
        // The regression case: TapCRM UAT 2026-07-23 recorded a 500 here
        // because PipelineDefinition's compact constructor threw before the
        // validator got to run. Post-fix, Jackson deserializes the blank-id
        // body cleanly and the validator produces an ID_BLANK error under
        // the "*" pipeline key.
        given: "the validator sees a blank-id definition and returns an ID_BLANK report"
        ValidationReport report = new ValidationReport.Builder()
                .addPipelineError("*", new io.jaiclaw.pipeline.validation.ValidationError(
                        "*", "pipeline", "ID_BLANK",
                        "Pipeline id must not be blank", null))
                .build()
        validator.validate({ PipelineDefinition d -> d.id() == "" }) >> report

        expect: "the endpoint the SPA calls when the user clears #pipe-id and hits Validate"
        mvc.perform(post("/api/pipeline-studio/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"id":"","name":"","enabled":true,"maxRetries":3,"stages":[]}'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.hasErrors').value(true))
                .andExpect(jsonPath('$.errors[0].code').value("ID_BLANK"))
                .andExpect(jsonPath('$.errors[0].pipelineId').value("*"))
                .andExpect(jsonPath('$.errors[0].location').value("pipeline"))
    }

    def "POST /validate with malformed JSON returns 200 + BODY_PARSE_ERROR via the @ExceptionHandler"() {
        // Defense-in-depth: even without Jackson choking on a value-layer
        // invariant, unclosed-brace / bad-token JSON should surface as a
        // structured ValidationReport, not a raw 500.
        expect:
        mvc.perform(post("/api/pipeline-studio/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content('{"id":"p1", not-json'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.hasErrors').value(true))
                .andExpect(jsonPath('$.errors[0].code').value("BODY_PARSE_ERROR"))
                .andExpect(jsonPath('$.errors[0].location').value("body"))
    }

    def "GET /drafts/{id}/variables lists placeholder set up to the requested stage"() {
        given:
        PipelineDefinition definition = new PipelineDefinition(
                "p1", "p1", null, [] as List<String>, true,
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                ErrorStrategy.STOP, 3, null,
                [
                    new StageDefinition("first", StageType.PROCESSOR, "b1", null, null, null, null, null, null),
                    new StageDefinition("second", StageType.PROCESSOR, "b2", null, null, null, null, null, null)
                ],
                new OutputDefinition(OutputType.NONE, null, null, null),
                PipelineSecurityProperties.DEFAULT,
                null)
        store.find("p1") >> Optional.of(new PipelineDraft("p1", 1, definition, null,
                PipelineDraft.Status.DRAFT, PipelineDraft.Origin.STUDIO, Instant.now()))

        expect: "asking about 'second' returns first's output but not second's own"
        mvc.perform(get("/api/pipeline-studio/drafts/p1/variables").param("stage", "second"))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.stage').value("second"))
                .andExpect(jsonPath('$.variables["stages.first.output"]').exists())
                .andExpect(jsonPath('$.variables["stages.second.output"]').doesNotExist())
                .andExpect(jsonPath('$.variables["pipeline.id"]').value("p1"))
    }
}
