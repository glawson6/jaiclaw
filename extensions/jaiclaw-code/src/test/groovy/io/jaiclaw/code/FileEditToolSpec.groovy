package io.jaiclaw.code

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolCatalog
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class FileEditToolSpec extends Specification {

    @TempDir
    Path tempDir

    ToolContext context
    FileEditTool tool

    def setup() {
        context = new ToolContext("agent", "session", "sid", tempDir.toString())
        tool = new FileEditTool()
    }

    def "replaces unique string successfully"() {
        given:
        Files.writeString(tempDir.resolve("test.txt"), "Hello World\nGoodbye World\n")

        when:
        def result = tool.execute(Map.of("path", "test.txt", "old_string", "Hello", "new_string", "Hi"), context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("Replaced 1 occurrence(s)")
        Files.readString(tempDir.resolve("test.txt")) == "Hi World\nGoodbye World\n"
    }

    def "returns error when old_string not found"() {
        given:
        Files.writeString(tempDir.resolve("test.txt"), "Hello World\n")

        when:
        def result = tool.execute(Map.of("path", "test.txt", "old_string", "Missing", "new_string", "X"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("not found")
    }

    def "returns error when old_string found multiple times without replace_all"() {
        given:
        Files.writeString(tempDir.resolve("test.txt"), "foo bar foo baz foo\n")

        when:
        def result = tool.execute(Map.of("path", "test.txt", "old_string", "foo", "new_string", "qux"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("3 times")
        (result as ToolResult.Error).message().contains("replace_all")
    }

    def "replaces all occurrences when replace_all is true"() {
        given:
        Files.writeString(tempDir.resolve("test.txt"), "foo bar foo baz foo\n")

        when:
        def result = tool.execute(Map.of(
                "path", "test.txt",
                "old_string", "foo",
                "new_string", "qux",
                "replace_all", "true"), context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("Replaced 3 occurrence(s)")
        Files.readString(tempDir.resolve("test.txt")) == "qux bar qux baz qux\n"
    }

    def "preserves line endings"() {
        given:
        def content = "line1\r\nline2\r\nline3\r\n"
        Files.writeString(tempDir.resolve("crlf.txt"), content)

        when:
        def result = tool.execute(Map.of("path", "crlf.txt", "old_string", "line2", "new_string", "updated"), context)

        then:
        result instanceof ToolResult.Success
        Files.readString(tempDir.resolve("crlf.txt")) == "line1\r\nupdated\r\nline3\r\n"
    }

    def "returns error for nonexistent file"() {
        when:
        def result = tool.execute(Map.of("path", "nope.txt", "old_string", "a", "new_string", "b"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("not found")
    }

    def "returns error when path is missing"() {
        when:
        def result = tool.execute(Map.of("old_string", "a", "new_string", "b"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Missing required parameter")
    }

    def "is in CODING profile"() {
        expect:
        tool.definition().isAvailableIn(ToolProfile.CODING)
    }

    def "is NOT in MINIMAL profile"() {
        expect:
        !tool.definition().isAvailableIn(ToolProfile.MINIMAL)
    }

    def "section is Files"() {
        expect:
        tool.definition().section() == ToolCatalog.SECTION_FILES
    }

    // --- Workspace boundary tests ---

    def "path traversal is blocked when workspace boundary is enforced"() {
        given:
        def boundedTool = new FileEditTool(true)
        Files.writeString(tempDir.resolve("test.txt"), "hello")

        when:
        def result = boundedTool.execute(Map.of("path", "../../etc/passwd", "old_string", "root", "new_string", "x"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Path traversal blocked")
    }

    def "path traversal is allowed when workspace boundary is not enforced"() {
        given:
        // Default tool (no boundary enforcement) - traversal resolves but file won't exist
        def result = tool.execute(Map.of("path", "../nonexistent", "old_string", "a", "new_string", "b"), context)

        expect:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("not found")
    }
}
