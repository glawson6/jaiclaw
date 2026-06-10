package io.jaiclaw.core.tool.schema

import spock.lang.Specification

/**
 * 0.8.0 P3.2: locks the JSON-Schema builder behaviour.
 */
class SchemaBuilderSpec extends Specification {

    def "builds a minimal object schema"() {
        when:
        String json = SchemaBuilder.object().toJsonString()

        then:
        json == '{"type":"object","properties":{},"required":[]}'
    }

    def "adds string + integer properties and marks them required"() {
        when:
        String json = SchemaBuilder.object()
                .property("url", new FieldSpec.StringField("The URL to fetch"))
                .property("timeout", new FieldSpec.IntegerField("Seconds", 1, 300))
                .required("url")
                .toJsonString()

        then:
        json.contains('"type":"object"')
        json.contains('"url":{"type":"string","description":"The URL to fetch"}')
        json.contains('"timeout":{"type":"integer","description":"Seconds","minimum":1,"maximum":300}')
        json.contains('"required":["url"]')
    }

    def "boolean property with default lands in the schema"() {
        when:
        String json = SchemaBuilder.object()
                .property("extractReadable",
                        new FieldSpec.BooleanField("Extract clean readable text", true))
                .toJsonString()

        then:
        json.contains('"extractReadable":{"type":"boolean","description":"Extract clean readable text","default":true}')
    }

    def "enum string is serialized as an enum array"() {
        when:
        String json = SchemaBuilder.object()
                .property("mode",
                        new FieldSpec.StringField("Operating mode", ["fast", "slow"], null))
                .toJsonString()

        then:
        json.contains('"mode":{"type":"string","description":"Operating mode","enum":["fast","slow"]}')
    }

    def "rejects null or blank property names"() {
        when:
        SchemaBuilder.object().property(name, new FieldSpec.StringField("x"))

        then:
        thrown(IllegalArgumentException)

        where:
        name | _
        null | _
        ""   | _
        "  " | _
    }

    def "rejects null FieldSpec"() {
        when:
        SchemaBuilder.object().property("x", null)

        then:
        thrown(IllegalArgumentException)
    }

    def "required(...) rejects names that haven't been added"() {
        when:
        SchemaBuilder.object()
                .property("url", new FieldSpec.StringField("u"))
                .required("missing")

        then:
        thrown(IllegalArgumentException)
    }

    def "escapes special characters in string values"() {
        when:
        String json = SchemaBuilder.object()
                .property("note", new FieldSpec.StringField("a \"quoted\" line\nbreak"))
                .toJsonString()

        then:
        json.contains('a \\"quoted\\" line\\nbreak')
    }

    def "ArrayField serializes with item subspec"() {
        when:
        String json = SchemaBuilder.object()
                .property("tags", new FieldSpec.ArrayField("Tags", new FieldSpec.StringField("tag")))
                .toJsonString()

        then:
        json.contains('"tags":{"type":"array","description":"Tags","items":{"type":"string","description":"tag"}}')
    }

    def "number field preserves min/max"() {
        when:
        String json = SchemaBuilder.object()
                .property("rate", new FieldSpec.NumberField("Rate", 0.0d, 1.0d))
                .toJsonString()

        then:
        json.contains('"rate":{"type":"number","description":"Rate","minimum":0.0,"maximum":1.0}')
    }
}
