package io.jaiclaw.pipeline.processors.transform

import io.jaiclaw.pipeline.PipelineContext
import io.jaiclaw.pipeline.StageDefinition
import io.jaiclaw.pipeline.StageType
import org.apache.camel.Exchange
import org.apache.camel.Message
import spock.lang.Specification

/**
 * Consolidated Transform-group processor spec — one interaction per
 * processor to prove wiring, config parsing, and edge cases. Every
 * processor also carries a {@link io.jaiclaw.pipeline.PipelineProcessor}
 * annotation checked in the autoconfig integration path.
 */
class TransformProcessorsSpec extends Specification {

    Exchange exchange = Mock()
    Message message = Mock()
    PipelineContext ctx = new PipelineContext(
            "pipe", "exec", null, "corr", 0, 1, null, null,
            [:] as Map, ["__input__": "hello world"] as Map)
    StageDefinition stage = new StageDefinition(
            "s1", StageType.PROCESSOR, "b", null, null, null, null, null, null)

    def setup() {
        exchange.getIn() >> message
    }

    def "Template renders {{input}}"() {
        given:
        TemplateProcessor p = new TemplateProcessor()

        when:
        p.process(exchange, stage, ctx, [template: "hello {{input}}!"])

        then:
        1 * message.setBody("hello hello world!")
    }

    def "Template with blank config is a no-op"() {
        given:
        TemplateProcessor p = new TemplateProcessor()

        when:
        p.process(exchange, stage, ctx, [:])

        then:
        0 * message.setBody(_)
    }

    def "RegexExtract pulls the requested capture group"() {
        given:
        RegexProcessors.Extract p = new RegexProcessors.Extract()
        message.getBody(String.class) >> "Order #12345 has 3 items"

        when:
        p.process(exchange, stage, ctx, [pattern: "Order #(\\d+)", group: "1"])

        then:
        1 * message.setBody("12345")
    }

    def "RegexExtract allMatches newline-joins every hit"() {
        given:
        RegexProcessors.Extract p = new RegexProcessors.Extract()
        message.getBody(String.class) >> "a1 b2 c3"

        when:
        p.process(exchange, stage, ctx, [pattern: "\\d", group: "0", allMatches: "true"])

        then:
        1 * message.setBody("1\n2\n3")
    }

    def "RegexReplace applies replacement globally"() {
        given:
        RegexProcessors.Replace p = new RegexProcessors.Replace()
        message.getBody(String.class) >> "foo bar foo baz"

        when:
        p.process(exchange, stage, ctx, [pattern: "foo", replacement: "X"])

        then:
        1 * message.setBody("X bar X baz")
    }

    def "TrimCaseTruncate composes trim + upper + max-length"() {
        given:
        TrimCaseTruncate p = new TrimCaseTruncate()
        message.getBody(String.class) >> "  Hello World  "

        when:
        p.process(exchange, stage, ctx,
                [trim: "true", case: "upper", maxLength: "5"])

        then:
        1 * message.setBody("HELLO")
    }

    def "TrimCaseTruncate title-cases correctly"() {
        given:
        TrimCaseTruncate p = new TrimCaseTruncate()
        message.getBody(String.class) >> "hello WORLD foo"

        when:
        p.process(exchange, stage, ctx, [case: "title"])

        then:
        1 * message.setBody("Hello World Foo")
    }

    def "JsonPathExtract extracts a field"() {
        given:
        JsonProcessors.PathExtract p = new JsonProcessors.PathExtract()
        message.getBody(String.class) >> '{"order":{"id":"ord-42","total":99}}'

        when:
        p.process(exchange, stage, ctx, [path: '$.order.id'])

        then:
        1 * message.setBody("ord-42")
    }

    def "JsonPathExtract missing path falls back to default"() {
        given:
        JsonProcessors.PathExtract p = new JsonProcessors.PathExtract()
        message.getBody(String.class) >> '{"a":1}'

        when:
        p.process(exchange, stage, ctx, [path: '$.missing', default: "n/a"])

        then:
        1 * message.setBody("n/a")
    }

    def "JsonValidate passes for a schema-conforming payload"() {
        given:
        JsonProcessors.Validate p = new JsonProcessors.Validate()
        message.getBody(String.class) >> '{"name":"Alice","age":30}'

        when:
        p.process(exchange, stage, ctx, [schema: '{"type":"object","required":["name","age"]}'])

        then:
        noExceptionThrown()
    }

    def "JsonValidate throws on schema violation"() {
        given:
        JsonProcessors.Validate p = new JsonProcessors.Validate()
        message.getBody(String.class) >> '{"name":"Alice"}'

        when:
        p.process(exchange, stage, ctx, [schema: '{"type":"object","required":["name","age"]}'])

        then:
        IllegalStateException e = thrown()
        e.message.contains("validation failed")
    }

    def "HtmlToText strips tags + decodes entities"() {
        given:
        FormatProcessors.HtmlToText p = new FormatProcessors.HtmlToText()
        message.getBody(String.class) >> "<p>Hello <b>world</b> &amp; friends</p>"

        when:
        p.process(exchange, stage, ctx, [:])

        then:
        1 * message.setBody("Hello world & friends")
    }

    def "MarkdownToHtml renders headings + inline styles"() {
        given:
        FormatProcessors.MarkdownToHtml p = new FormatProcessors.MarkdownToHtml()
        message.getBody(String.class) >> "# Title\n\nSome **bold** and *italic* text."

        when:
        p.process(exchange, stage, ctx, [:])

        then:
        1 * message.setBody({ String html ->
            html.contains("<h1>Title</h1>") &&
                    html.contains("<strong>bold</strong>") &&
                    html.contains("<em>italic</em>")
        })
    }

    def "Chunk splits input larger than maxSize"() {
        given:
        FormatProcessors.Chunk p = new FormatProcessors.Chunk()
        message.getBody(String.class) >> ("x" * 25)

        when:
        p.process(exchange, stage, ctx, [maxSize: "10", overlap: "0"])

        then:
        1 * message.setBody({ String out ->
            // 25 chars / 10 chars/step + no overlap = 3 chunks
            out.count("---chunk---") == 2
        })
    }

    def "Chunk passes short input straight through"() {
        given:
        FormatProcessors.Chunk p = new FormatProcessors.Chunk()
        message.getBody(String.class) >> "short"

        when:
        p.process(exchange, stage, ctx, [maxSize: "10"])

        then:
        1 * message.setBody("short")
    }

    def "XmlToJson serialises XML to JSON"() {
        given:
        FormatProcessors.XmlToJson p = new FormatProcessors.XmlToJson()
        message.getBody(String.class) >> "<order><id>42</id><total>99</total></order>"

        when:
        p.process(exchange, stage, ctx, [:])

        then:
        1 * message.setBody({ String json ->
            json.contains('"id":"42"') && json.contains('"total":"99"')
        })
    }
}
