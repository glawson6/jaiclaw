package io.jclaw.code

import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolProfile
import io.jclaw.core.tool.ToolResult
import io.jclaw.tools.ToolCatalog
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GlobToolSpec extends Specification {

    @TempDir
    Path tempDir

    ToolContext context
    GlobTool tool

    def setup() {
        context = new ToolContext("agent", "session", "sid", tempDir.toString())
        tool = new GlobTool()
    }

    def "finds Java files recursively with **/*.java"() {
        given:
        Files.createDirectories(tempDir.resolve("src/main"))
        Files.writeString(tempDir.resolve("src/main/Foo.java"), "class Foo {}")
        Files.writeString(tempDir.resolve("src/main/Bar.java"), "class Bar {}")
        Files.writeString(tempDir.resolve("README.md"), "# readme")

        when:
        def result = tool.execute(Map.of("pattern", "**/*.java"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("Foo.java")
        content.contains("Bar.java")
        !content.contains("README.md")
    }

    def "finds only root-level txt files with *.txt"() {
        given:
        Files.writeString(tempDir.resolve("root.txt"), "root")
        Files.createDirectories(tempDir.resolve("sub"))
        Files.writeString(tempDir.resolve("sub/nested.txt"), "nested")

        when:
        def result = tool.execute(Map.of("pattern", "*.txt"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("root.txt")
        !content.contains("nested.txt")
    }

    def "skips .git directory"() {
        given:
        Files.createDirectories(tempDir.resolve(".git/objects"))
        Files.writeString(tempDir.resolve(".git/objects/abc.txt"), "git object")
        Files.writeString(tempDir.resolve("real.txt"), "real file")

        when:
        def result = tool.execute(Map.of("pattern", "**/*.txt"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("real.txt")
        !content.contains("abc.txt")
    }

    def "skips target directory"() {
        given:
        Files.createDirectories(tempDir.resolve("target/classes"))
        Files.writeString(tempDir.resolve("target/classes/App.class"), "bytecode")
        Files.writeString(tempDir.resolve("App.java"), "class App {}")

        when:
        def result = tool.execute(Map.of("pattern", "**/*"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("App.java")
        !content.contains("App.class")
    }

    def "returns sorted results"() {
        given:
        Files.writeString(tempDir.resolve("c.txt"), "c")
        Files.writeString(tempDir.resolve("a.txt"), "a")
        Files.writeString(tempDir.resolve("b.txt"), "b")

        when:
        def result = tool.execute(Map.of("pattern", "*.txt"), context)

        then:
        result instanceof ToolResult.Success
        def lines = (result as ToolResult.Success).content().split("\n") as List
        lines == ["a.txt", "b.txt", "c.txt"]
    }

    def "returns message for no matches"() {
        when:
        def result = tool.execute(Map.of("pattern", "**/*.xyz"), context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("No files matched")
    }

    def "returns error for nonexistent base path"() {
        when:
        def result = tool.execute(Map.of("pattern", "**/*.java", "path", "nonexistent"), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("not found")
    }

    def "respects path parameter"() {
        given:
        Files.createDirectories(tempDir.resolve("src"))
        Files.createDirectories(tempDir.resolve("docs"))
        Files.writeString(tempDir.resolve("src/Foo.java"), "class Foo {}")
        Files.writeString(tempDir.resolve("docs/guide.txt"), "guide")

        when:
        def result = tool.execute(Map.of("pattern", "**/*", "path", "src"), context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("Foo.java")
        !content.contains("guide.txt")
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
