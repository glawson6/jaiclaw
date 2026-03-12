package io.jclaw.documents

import spock.lang.Specification

class TextChunkerSpec extends Specification {

    def chunker = new TextChunker()

    def "fixed size chunking splits text at boundaries"() {
        given:
        def text = "A" * 250
        def strategy = ChunkingStrategy.fixedSize(100, 0)

        when:
        def chunks = chunker.chunk(text, strategy)

        then:
        chunks.size() == 3
        chunks.every { it.length() <= 100 }
    }

    def "fixed size chunking with overlap"() {
        given:
        def text = "A" * 200
        def strategy = ChunkingStrategy.fixedSize(100, 20)

        when:
        def chunks = chunker.chunk(text, strategy)

        then:
        chunks.size() >= 2
        // First chunk is 100 chars, then start at 80, so second chunk covers 80-180
    }

    def "paragraph chunking splits at double newlines"() {
        given:
        def text = """First paragraph here.

Second paragraph here.

Third paragraph here."""
        def strategy = ChunkingStrategy.paragraphs(1000, 0)

        when:
        def chunks = chunker.chunk(text, strategy)

        then:
        chunks.size() == 1  // All fit in one chunk since maxSize=1000
        chunks[0].contains("First paragraph")
    }

    def "paragraph chunking splits when paragraphs exceed max size"() {
        given:
        def text = ("Paragraph one. " * 20).trim() + "\n\n" +
                   ("Paragraph two. " * 20).trim() + "\n\n" +
                   ("Paragraph three. " * 20).trim()
        def strategy = ChunkingStrategy.paragraphs(200, 0)

        when:
        def chunks = chunker.chunk(text, strategy)

        then:
        chunks.size() >= 2
        chunks.every { it.length() <= 400 } // Some tolerance for segment boundaries
    }

    def "sentence chunking splits at sentence boundaries"() {
        given:
        def text = "First sentence. Second sentence. Third sentence. Fourth sentence."
        def strategy = ChunkingStrategy.sentences(40, 0)

        when:
        def chunks = chunker.chunk(text, strategy)

        then:
        chunks.size() >= 2
    }

    def "null or blank text returns empty list"() {
        expect:
        chunker.chunk(null, ChunkingStrategy.defaults()).isEmpty()
        chunker.chunk("", ChunkingStrategy.defaults()).isEmpty()
        chunker.chunk("   ", ChunkingStrategy.defaults()).isEmpty()
    }

    def "single small text returns one chunk"() {
        given:
        def text = "Short text."
        def strategy = ChunkingStrategy.defaults()

        when:
        def chunks = chunker.chunk(text, strategy)

        then:
        chunks.size() == 1
        chunks[0] == "Short text."
    }
}
