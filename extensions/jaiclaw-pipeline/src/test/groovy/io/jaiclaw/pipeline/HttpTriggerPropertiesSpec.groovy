package io.jaiclaw.pipeline

import io.jaiclaw.pipeline.PipelineProperties.HttpTriggerProperties
import spock.lang.Specification

/**
 * Binding + invariant coverage for {@link HttpTriggerProperties}. The
 * {@code allowed} alias map landed in 0.9.1 to give the HTTP trigger
 * surface a safe-by-default posture (empty map → no pipelines callable
 * via HTTP) and to reject malformed YAML entries at startup rather
 * than letting them silently expose every pipeline.
 */
class HttpTriggerPropertiesSpec extends Specification {

    def "DEFAULT has no aliases — safe-by-default"() {
        expect:
        HttpTriggerProperties.DEFAULT.allowed().isEmpty()
        HttpTriggerProperties.DEFAULT.basePath() == "/api/pipelines"
        HttpTriggerProperties.DEFAULT.enabled()
    }

    def "blank basePath falls back to /api/pipelines"() {
        when:
        HttpTriggerProperties props = new HttpTriggerProperties(true, "  ", ["alias": "pipe"])

        then:
        props.basePath() == "/api/pipelines"
        props.allowed() == [alias: "pipe"]
    }

    def "null allowed map is coerced to an empty map"() {
        when:
        HttpTriggerProperties props = new HttpTriggerProperties(true, "/api/p", null)

        then:
        props.allowed() == [:]
    }

    def "blank alias keys throw at construction (fail-fast on bad YAML)"() {
        when:
        new HttpTriggerProperties(true, "/api/p", ["   ": "embabel-pipe"])

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("alias must not be blank")
    }

    def "blank pipeline id values throw at construction"() {
        when:
        new HttpTriggerProperties(true, "/api/p", ["ticket-scoring": ""])

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("ticket-scoring")
        ex.message.contains("must not be blank")
    }

    def "constructor preserves Spring's bound map shape (no defensive copy that would break record-binding)"() {
        given: "a regular HashMap as Spring's binder would supply"
        Map<String, String> src = new LinkedHashMap<>()
        src.put("ticket-scoring", "embabel-pipe")
        src.put("refund-review", "refund-pipe")

        when:
        HttpTriggerProperties props = new HttpTriggerProperties(true, "/api/p", src)

        then: "the alias-to-pipelineId entries round-trip through accessor"
        props.allowed().size() == 2
        props.allowed()["ticket-scoring"] == "embabel-pipe"
        props.allowed()["refund-review"] == "refund-pipe"
    }
}
