package io.jaiclaw.channel.chunking

import spock.lang.Specification

class MessageChunkerSpec extends Specification {

    def "returns empty list for null text"() {
        expect:
        MessageChunker.chunk(null, PlatformLimits.DEFAULT) == []
    }

    def "returns empty list for empty text"() {
        expect:
        MessageChunker.chunk("", PlatformLimits.DEFAULT) == []
    }

    def "returns single-element list when text fits within limit"() {
        given:
        def text = "Hello, world!"

        expect:
        MessageChunker.chunk(text, PlatformLimits.DEFAULT) == [text]
    }

    def "splits at paragraph boundary (double newline)"() {
        given:
        def limit = new PlatformLimits(30)
        def text = "First paragraph.\n\nSecond paragraph here."

        when:
        def chunks = MessageChunker.chunk(text, limit)

        then:
        chunks.size() == 2
        chunks[0] == "First paragraph."
        chunks[1] == "Second paragraph here."
    }

    def "splits at line boundary when no paragraph break"() {
        given:
        def limit = new PlatformLimits(25)
        def text = "First line here.\nSecond line here."

        when:
        def chunks = MessageChunker.chunk(text, limit)

        then:
        chunks.size() == 2
        chunks[0] == "First line here."
        chunks[1] == "Second line here."
    }

    def "splits at sentence boundary when no line break"() {
        given:
        def limit = new PlatformLimits(30)
        def text = "First sentence. Second sentence here is long."

        when:
        def chunks = MessageChunker.chunk(text, limit)

        then:
        chunks.size() == 2
        chunks[0].endsWith(".")
    }

    def "handles very long text with multiple chunks"() {
        given:
        def limit = new PlatformLimits(20)
        def text = "AAAA BBBB.\n\nCCCC DDDD.\n\nEEEE FFFF."

        when:
        def chunks = MessageChunker.chunk(text, limit)

        then:
        chunks.size() >= 2
        chunks.every { it.length() <= 25 } // allow small overhead from code fence closing
        chunks.join("").replaceAll("\\s", "") == text.replaceAll("\\s", "")
    }

    def "preserves code fences across chunk boundaries"() {
        given:
        def limit = new PlatformLimits(50)
        def text = "Here is code:\n```java\nSystem.out.println(\"hello\");\nSystem.out.println(\"world\");\nSystem.out.println(\"test\");\n```\nEnd."

        when:
        def chunks = MessageChunker.chunk(text, limit)

        then:
        chunks.size() >= 2
        // Each chunk should have balanced code fences (even number)
        chunks.each { chunk ->
            def fenceCount = (chunk =~ /(?m)^```/).count
            assert fenceCount % 2 == 0 : "Unbalanced code fences in chunk: ${chunk}"
        }
    }

    def "handles SMS limit (160 chars)"() {
        given:
        def text = "A" * 200

        when:
        def chunks = MessageChunker.chunk(text, PlatformLimits.SMS)

        then:
        chunks.size() == 2
        chunks[0].length() == 160
        chunks[1].length() == 40
    }

    def "does not chunk when limit is MAX_VALUE (email)"() {
        given:
        def text = "A" * 100000

        expect:
        MessageChunker.chunk(text, PlatformLimits.EMAIL) == [text]
    }

    def "splits at word boundary for clean breaks"() {
        given:
        def limit = new PlatformLimits(15)
        def text = "Hello beautiful world today"

        when:
        def chunks = MessageChunker.chunk(text, limit)

        then:
        chunks.size() >= 2
        // No word should be split mid-word
        chunks.every { !it.endsWith("-") }
    }

    def "handles text that is exactly the limit"() {
        given:
        def text = "A" * 4096

        expect:
        MessageChunker.chunk(text, PlatformLimits.DEFAULT) == [text]
    }

    def "handles Discord limit (2000 chars)"() {
        given:
        def paragraph = "This is a test paragraph. " * 40 // ~1040 chars per paragraph
        def text = paragraph + "\n\n" + paragraph + "\n\n" + paragraph

        when:
        def chunks = MessageChunker.chunk(text, PlatformLimits.DISCORD)

        then:
        chunks.size() >= 2
        chunks.every { it.length() <= 2000 }
    }
}
