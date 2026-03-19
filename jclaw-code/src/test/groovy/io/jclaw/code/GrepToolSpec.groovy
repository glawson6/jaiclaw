package io.jclaw.code

import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolProfile
import io.jclaw.core.tool.ToolResult
import io.jclaw.tools.ToolCatalog
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GrepToolSpec extends Specification {

    @TempDir
    Path tempDir

    ToolContext context
    GrepTool tool

    def setup() {
        context = new ToolContext("agent", "session", "sid", tempDir.toString())
        tool = new GrepTool()
    }

    def "finds simple string matches"() {
        given:
        Files.writeString(tempDir.resolve("test.txt"), "hello world\nfoo bar\nhello again\n")

        when:
        def result = tool.execute(Map.of("pattern", "hello"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("test.txt:1: hello world")
        content.contains("test.txt:3: hello again")
    }

    def "supports regex patterns"() {
        given:
        Files.writeString(tempDir.resolve("code.java"), "log.info(msg)\nlog.warn(err)\nSystem.out.println(x)\n")

        when:
        def result = tool.execute(Map.of("pattern", "log\\.\\w+\\("), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("log.info(")
        content.contains("log.warn(")
        !content.contains("System.out")
    }

    def "returns error for invalid regex"() {
        when:
        def result = tool.execute(Map.of("pattern", "[invalid"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Invalid regex")
    }

    def "glob filter restricts file types"() {
        given:
        Files.writeString(tempDir.resolve("App.java"), "hello from java\n")
        Files.writeString(tempDir.resolve("App.txt"), "hello from txt\n")

        when:
        def result = tool.execute(Map.of("pattern", "hello", "glob", "*.java"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("App.java")
        !content.contains("App.txt")
    }

    def "includes context lines when requested"() {
        given:
        Files.writeString(tempDir.resolve("test.txt"), "line1\nline2\nMATCH\nline4\nline5\n")

        when:
        def result = tool.execute(Map.of("pattern", "MATCH", "context", 1), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("test.txt:2- line2")
        content.contains("test.txt:3: MATCH")
        content.contains("test.txt:4- line4")
    }

    def "skips binary files"() {
        given:
        Files.writeString(tempDir.resolve("text.txt"), "hello\n")
        // Write a file with null bytes (binary)
        byte[] binary = new byte[100]
        binary[0] = 0x48 // 'H'
        binary[1] = 0x00 // null byte
        binary[2] = 0x65 // 'e'
        Files.write(tempDir.resolve("binary.bin"), binary)

        when:
        def result = tool.execute(Map.of("pattern", ".*"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("text.txt")
        !content.contains("binary.bin")
    }

    def "caps results at max_results"() {
        given:
        def lines = (1..100).collect { "match line $it" }.join("\n")
        Files.writeString(tempDir.resolve("big.txt"), lines)

        when:
        def result = tool.execute(Map.of("pattern", "match", "max_results", 5), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("capped at 5")
    }

    def "returns message for no matches"() {
        given:
        Files.writeString(tempDir.resolve("test.txt"), "hello world\n")

        when:
        def result = tool.execute(Map.of("pattern", "zzzzz"), context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("No matches found")
    }

    def "skips excluded directories"() {
        given:
        Files.createDirectories(tempDir.resolve("target/classes"))
        Files.writeString(tempDir.resolve("target/classes/Foo.txt"), "match\n")
        Files.writeString(tempDir.resolve("real.txt"), "match\n")

        when:
        def result = tool.execute(Map.of("pattern", "match"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("real.txt")
        !content.contains("Foo.txt")
    }

    def "searches a single file when path points to file"() {
        given:
        Files.writeString(tempDir.resolve("a.txt"), "hello\n")
        Files.writeString(tempDir.resolve("b.txt"), "hello\n")

        when:
        def result = tool.execute(Map.of("pattern", "hello", "path", "a.txt"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("a.txt")
        !content.contains("b.txt")
    }

    def "is in MINIMAL profile"() {
        expect:
        tool.definition().isAvailableIn(ToolProfile.MINIMAL)
    }

    def "section is Files"() {
        expect:
        tool.definition().section() == ToolCatalog.SECTION_FILES
    }
}
