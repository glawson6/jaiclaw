package io.jaiclaw.compaction

import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

class TruncatingToolResultCompressorSpec extends Specification {

    def compressor = new TruncatingToolResultCompressor()

    def "content within budget is returned unchanged"() {
        given:
        def result = new ToolResult.Success("short text")

        when:
        def compressed = compressor.compress(result, 1000)

        then:
        compressed.content() == "short text"
    }

    def "content exceeding budget is truncated with marker"() {
        given:
        def longContent = "x" * 500
        def result = new ToolResult.Success(longContent)

        when:
        def compressed = compressor.compress(result, 10)  // 10 tokens * 4 chars = 40 chars budget

        then:
        compressed.content().length() <= 40
        compressed.content().endsWith("... [truncated]")
    }

    def "supports returns true for non-empty content"() {
        expect:
        compressor.supports(new ToolResult.Success("some content"))
        !compressor.supports(new ToolResult.Success(""))
        !compressor.supports(new ToolResult.Success(null))
    }
}
