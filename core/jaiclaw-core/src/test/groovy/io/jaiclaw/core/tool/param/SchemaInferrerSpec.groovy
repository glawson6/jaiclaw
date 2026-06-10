package io.jaiclaw.core.tool.param

import spock.lang.Specification

/**
 * 0.8.0 P3.2: locks JSON-Schema inference over record components.
 */
class SchemaInferrerSpec extends Specification {

    static record FetchParams(
            @ToolParameter(description = "The URL to fetch") String url,
            @ToolParameter(description = "Timeout in seconds", required = false) Integer timeout,
            @ToolParameter(description = "Extract readable text", required = false) Boolean extractReadable
    ) {}

    static record UnannotatedParams(
            String name,
            @ToolParameter(description = "ok") Integer value
    ) {}

    static record AllTypes(
            @ToolParameter(description = "s") String s,
            @ToolParameter(description = "i") int i,
            @ToolParameter(description = "l") Long l,
            @ToolParameter(description = "d") Double d,
            @ToolParameter(description = "b") boolean b,
            @ToolParameter(description = "list") java.util.List items
    ) {}

    def "infers schema with required+optional fields"() {
        when:
        String json = SchemaInferrer.inferSchemaString(FetchParams.class)

        then:
        json.contains('"url":{"type":"string","description":"The URL to fetch"}')
        json.contains('"timeout":{"type":"integer","description":"Timeout in seconds"}')
        json.contains('"extractReadable":{"type":"boolean","description":"Extract readable text"}')
        json.contains('"required":["url"]')
    }

    def "rejects non-record"() {
        when:
        SchemaInferrer.inferSchemaString(String.class)

        then:
        thrown(IllegalArgumentException)
    }

    def "fails when any component lacks @ToolParameter"() {
        when:
        SchemaInferrer.inferSchemaString(UnannotatedParams.class)

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("@ToolParameter")
        ex.message.contains("name")
    }

    def "covers all supported scalar types + List"() {
        when:
        String json = SchemaInferrer.inferSchemaString(AllTypes.class)

        then:
        json.contains('"s":{"type":"string"')
        json.contains('"i":{"type":"integer"')
        json.contains('"l":{"type":"integer"')
        json.contains('"d":{"type":"number"')
        json.contains('"b":{"type":"boolean"')
        json.contains('"items":{"type":"array"')
    }
}
