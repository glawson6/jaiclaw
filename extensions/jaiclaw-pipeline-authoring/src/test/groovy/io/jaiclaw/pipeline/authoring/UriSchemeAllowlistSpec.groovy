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
import spock.lang.Specification

/**
 * URI-scheme allowlist covers CAMEL stage URIs, CAMEL_URI triggers,
 * CAMEL_URI outputs, and per-stage transport URIs. Non-UI origins
 * (YAML_IMPORT, CODE_BEAN) skip the check entirely.
 */
class UriSchemeAllowlistSpec extends Specification {

    UriSchemeAllowlist allowlist = new UriSchemeAllowlist(
            PipelineSecurityProperties.DEFAULT_ALLOWED_URI_SCHEMES)

    private static PipelineDefinition build(TriggerDefinition trigger,
                                             List<StageDefinition> stages,
                                             OutputDefinition output) {
        return new PipelineDefinition(
                "p1", "p1", null, [] as List<String>, true,
                trigger,
                ErrorStrategy.STOP, 3, null,
                stages,
                output,
                PipelineSecurityProperties.DEFAULT,
                null)
    }

    def "clean pipeline with only allowed schemes passes"() {
        given:
        PipelineDefinition pipeline = build(
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                [new StageDefinition("s1", StageType.CAMEL, null, null, null, null,
                        "seda:my-queue", null, null)],
                new OutputDefinition(OutputType.LOG, null, null, null))

        when:
        def report = allowlist.check(pipeline, PipelineDraft.Origin.STUDIO)

        then:
        !report.hasErrors()
    }

    def "disallowed CAMEL stage URI is rejected for STUDIO origin"() {
        given:
        PipelineDefinition pipeline = build(
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                [new StageDefinition("evil", StageType.CAMEL, null, null, null, null,
                        "exec:rm -rf /", null, null)],
                new OutputDefinition(OutputType.NONE, null, null, null))

        when:
        def report = allowlist.check(pipeline, PipelineDraft.Origin.STUDIO)

        then:
        report.hasErrors()
        report.byPipeline()["p1"].any { it.code() == "URI_SCHEME_DENIED" }
    }

    def "disallowed CAMEL_URI trigger is rejected"() {
        given:
        PipelineDefinition pipeline = build(
                new TriggerDefinition(TriggerType.CAMEL_URI, "http:evil.example.com/callback", null, null),
                [new StageDefinition("s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.LOG, null, null, null))

        when:
        def report = allowlist.check(pipeline, PipelineDraft.Origin.STUDIO)

        then:
        report.hasErrors()
        report.byPipeline()["p1"].any { it.code() == "URI_SCHEME_DENIED" && it.location() == "trigger" }
    }

    def "disallowed CAMEL_URI output is rejected"() {
        given:
        PipelineDefinition pipeline = build(
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                [new StageDefinition("s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)],
                new OutputDefinition(OutputType.CAMEL_URI, null, "file:///etc/passwd", null))

        when:
        def report = allowlist.check(pipeline, PipelineDraft.Origin.STUDIO)

        then:
        report.hasErrors()
        report.byPipeline()["p1"].any { it.code() == "URI_SCHEME_DENIED" && it.location() == "output" }
    }

    def "YAML_IMPORT and CODE_BEAN origins skip the check"() {
        given:
        PipelineDefinition pipeline = build(
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                [new StageDefinition("evil", StageType.CAMEL, null, null, null, null,
                        "exec:whatever", null, null)],
                new OutputDefinition(OutputType.NONE, null, null, null))

        expect:
        !allowlist.check(pipeline, PipelineDraft.Origin.YAML_IMPORT).hasErrors()
        !allowlist.check(pipeline, PipelineDraft.Origin.CODE_BEAN).hasErrors()
    }

    def "malformed URI (no scheme) is flagged"() {
        given:
        PipelineDefinition pipeline = build(
                new TriggerDefinition(TriggerType.MANUAL, null, null, null),
                [new StageDefinition("s1", StageType.CAMEL, null, null, null, null,
                        "notauri", null, null)],
                new OutputDefinition(OutputType.NONE, null, null, null))

        when:
        def report = allowlist.check(pipeline, PipelineDraft.Origin.STUDIO)

        then:
        report.hasErrors()
        report.byPipeline()["p1"].any { it.code() == "MALFORMED_URI" }
    }

    def "isAllowed helper mirrors the same rules"() {
        expect:
        allowlist.isAllowed("seda:x")
        allowlist.isAllowed("log:foo")
        !allowlist.isAllowed("exec:rm")
        !allowlist.isAllowed("http://bad")
        allowlist.isAllowed("")     // blank = nothing to check
        allowlist.isAllowed(null)
    }
}
