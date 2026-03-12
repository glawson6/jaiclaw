package io.jclaw.documents

import spock.lang.Specification

class DocumentIngestionPipelineSpec extends Specification {

    def pipeline = new DocumentIngestionPipeline(CompositeDocumentParser.withDefaults())

    def "supports returns true for known MIME types"() {
        expect:
        pipeline.supports("application/pdf")
        pipeline.supports("text/html")
        pipeline.supports("text/plain")
        !pipeline.supports("video/mp4")
    }

    def "ingest plain text document"() {
        given:
        def text = "Hello world. This is a test document."

        when:
        def result = pipeline.ingest(text.bytes, "text/plain")

        then:
        result.text() == text
        result.chunkCount() >= 1
        result.metadata().get("mimeType") == "text/plain"
    }

    def "ingest HTML document"() {
        given:
        def html = "<html><head><title>Test</title></head><body><p>HTML content here.</p></body></html>"

        when:
        def result = pipeline.ingest(html.bytes, "text/html")

        then:
        result.text().contains("HTML content here")
        result.chunkCount() >= 1
    }

    def "ingest with custom chunking strategy"() {
        given:
        def text = ("Word " * 500).trim()
        def strategy = ChunkingStrategy.fixedSize(100, 10)

        when:
        def result = pipeline.ingest(text.bytes, "text/plain", strategy)

        then:
        result.chunkCount() > 1
    }

    def "ingest throws for unsupported MIME type"() {
        when:
        pipeline.ingest("data".bytes, "video/mp4")

        then:
        thrown(DocumentParseException)
    }
}
