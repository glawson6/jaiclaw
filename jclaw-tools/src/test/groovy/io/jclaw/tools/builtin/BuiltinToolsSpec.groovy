package io.jclaw.tools.builtin

import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolProfile
import io.jclaw.core.tool.ToolResult
import io.jclaw.tools.ToolCatalog
import io.jclaw.tools.ToolRegistry
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class BuiltinToolsSpec extends Specification {

    @TempDir
    Path tempDir

    ToolContext context

    def setup() {
        context = new ToolContext("agent", "session", "sid", tempDir.toString())
    }

    // --- BuiltinTools factory ---

    def "BuiltinTools.all() returns 5 tools"() {
        expect:
        BuiltinTools.all().size() == 5
    }

    def "BuiltinTools.registerAll populates registry"() {
        given:
        def registry = new ToolRegistry()

        when:
        BuiltinTools.registerAll(registry)

        then:
        registry.size() == 5
        registry.contains("file_read")
        registry.contains("file_write")
        registry.contains("shell_exec")
        registry.contains("web_fetch")
        registry.contains("web_search")
    }

    // --- FileReadTool ---

    def "FileReadTool reads file content with line numbers"() {
        given:
        def file = Files.writeString(tempDir.resolve("test.txt"), "line one\nline two\nline three\n")
        def tool = new FileReadTool()

        when:
        def result = tool.execute(Map.of("path", "test.txt"), context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("line one")
        (result as ToolResult.Success).content().contains("line two")
    }

    def "FileReadTool respects offset and limit"() {
        given:
        def lines = (1..10).collect { "line $it" }.join("\n")
        Files.writeString(tempDir.resolve("big.txt"), lines)
        def tool = new FileReadTool()

        when:
        def result = tool.execute(Map.of("path", "big.txt", "offset", 2, "limit", 3), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("line 3")
        content.contains("line 4")
        content.contains("line 5")
        !content.contains("line 1")
        !content.contains("line 6")
    }

    def "FileReadTool returns error for nonexistent file"() {
        given:
        def tool = new FileReadTool()

        when:
        def result = tool.execute(Map.of("path", "nope.txt"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("not found")
    }

    def "FileReadTool is in MINIMAL profile"() {
        expect:
        new FileReadTool().definition().isAvailableIn(ToolProfile.MINIMAL)
    }

    def "FileReadTool section is Files"() {
        expect:
        new FileReadTool().definition().section() == ToolCatalog.SECTION_FILES
    }

    // --- FileWriteTool ---

    def "FileWriteTool writes content to file"() {
        given:
        def tool = new FileWriteTool()

        when:
        def result = tool.execute(Map.of("path", "output.txt", "content", "hello world"), context)

        then:
        result instanceof ToolResult.Success
        Files.readString(tempDir.resolve("output.txt")) == "hello world"
    }

    def "FileWriteTool creates parent directories"() {
        given:
        def tool = new FileWriteTool()

        when:
        def result = tool.execute(Map.of("path", "sub/dir/file.txt", "content", "nested"), context)

        then:
        result instanceof ToolResult.Success
        Files.readString(tempDir.resolve("sub/dir/file.txt")) == "nested"
    }

    def "FileWriteTool returns error when content is missing"() {
        given:
        def tool = new FileWriteTool()

        when:
        def result = tool.execute(Map.of("path", "f.txt"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Missing required parameter")
    }

    def "FileWriteTool is NOT in MINIMAL profile"() {
        expect:
        !new FileWriteTool().definition().isAvailableIn(ToolProfile.MINIMAL)
    }

    // --- ShellExecTool ---

    def "ShellExecTool executes a command and returns output"() {
        given:
        def tool = new ShellExecTool()

        when:
        def result = tool.execute(Map.of("command", "echo 'hello from shell'"), context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().trim() == "hello from shell"
    }

    def "ShellExecTool returns error for failing command"() {
        given:
        def tool = new ShellExecTool()

        when:
        def result = tool.execute(Map.of("command", "exit 42"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Exit code 42")
    }

    def "ShellExecTool returns error when command is missing"() {
        given:
        def tool = new ShellExecTool()

        when:
        def result = tool.execute(Map.of(), context)

        then:
        result instanceof ToolResult.Error
    }

    // --- Tool definitions ---

    def "all built-in tools have non-blank input schemas"() {
        expect:
        BuiltinTools.all().every {
            def schema = it.definition().inputSchema()
            schema != null && !schema.isBlank() && schema.contains("type")
        }
    }

    def "all built-in tools have non-blank names and descriptions"() {
        expect:
        BuiltinTools.all().every {
            !it.definition().name().isBlank() && !it.definition().description().isBlank()
        }
    }

    def "all built-in tools are available in FULL profile"() {
        expect:
        BuiltinTools.all().every { it.definition().isAvailableIn(ToolProfile.FULL) }
    }
}
