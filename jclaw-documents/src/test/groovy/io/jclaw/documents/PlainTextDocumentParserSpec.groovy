package io.jclaw.documents

import spock.lang.Specification
import spock.lang.Unroll

class PlainTextDocumentParserSpec extends Specification {

    def parser = new PlainTextDocumentParser()

    @Unroll
    def "supports('#mimeType') returns #expected"() {
        expect:
        parser.supports(mimeType) == expected

        where:
        mimeType                | expected
        "text/plain"            | true
        "text/csv"              | true
        "text/markdown"         | true
        "application/pdf"       | false
        "text/html"             | false
        null                    | false
    }

    def "parse returns content as-is"() {
        given:
        def content = "Hello, world!\nThis is a test."

        when:
        def result = parser.parse(content.bytes, "text/plain")

        then:
        result.text() == content
        result.metadata().get("mimeType") == "text/plain"
    }
}
