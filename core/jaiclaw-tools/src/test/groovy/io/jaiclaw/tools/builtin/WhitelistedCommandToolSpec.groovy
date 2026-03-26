package io.jaiclaw.tools.builtin

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolCatalog
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class WhitelistedCommandToolSpec extends Specification {

    @TempDir
    Path tempDir

    def defaultConfig = new WhitelistedCommandConfig(
            ["df", "free", "uptime", "echo", "cat /proc/"],
            60,
            500,
            "test_command",
            "Monitoring",
            Set.of(ToolProfile.FULL)
    )

    def tool = new WhitelistedCommandTool(defaultConfig)
    def context = new ToolContext("agent", "session", "sid", "/tmp")

    // --- WhitelistedCommandConfig defaults ---

    def "config uses defaults for null/empty values"() {
        when:
        def config = new WhitelistedCommandConfig(null, 0, 0, null, null, null)

        then:
        config.allowedPrefixes() == []
        config.timeoutSeconds() == 60
        config.maxOutputLines() == 500
        config.toolName() == "whitelisted_command"
        config.section() == ToolCatalog.SECTION_EXEC
        config.profiles() == Set.of(ToolProfile.FULL)
    }

    def "config preserves provided values"() {
        when:
        def config = new WhitelistedCommandConfig(
                ["ls", "cat"],
                30,
                100,
                "my_tool",
                "Custom",
                Set.of(ToolProfile.CODING)
        )

        then:
        config.allowedPrefixes() == ["ls", "cat"]
        config.timeoutSeconds() == 30
        config.maxOutputLines() == 100
        config.toolName() == "my_tool"
        config.section() == "Custom"
        config.profiles() == Set.of(ToolProfile.CODING)
    }

    // --- WhitelistedCommandTool metadata ---

    def "tool has correct name and section from config"() {
        expect:
        tool.definition().name() == "test_command"
        tool.definition().section() == "Monitoring"
    }

    def "tool is available in configured profiles"() {
        expect:
        tool.definition().isAvailableIn(ToolProfile.FULL)
    }

    // --- Command allowlist ---

    def "allows whitelisted command prefixes"() {
        expect:
        tool.isCommandAllowed(command)

        where:
        command             | _
        "df -h"             | _
        "free -m"           | _
        "uptime"            | _
        "echo hello"        | _
        "cat /proc/meminfo" | _
    }

    def "rejects non-whitelisted commands"() {
        expect:
        !tool.isCommandAllowed(command)

        where:
        command                | _
        "rm -rf /"             | _
        "reboot"               | _
        "shutdown now"         | _
        "apt-get install vim"  | _
        "curl http://evil.com" | _
        "chmod 777 /"          | _
    }

    def "rejects empty command"() {
        expect:
        !tool.isCommandAllowed("")
        !tool.isCommandAllowed("  ")
    }

    def "returns error for disallowed command"() {
        when:
        def result = tool.execute(["command": "rm -rf /"], context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Command not allowed")
    }

    // --- Binary availability check ---

    def "returns error when binary is not on PATH"() {
        given:
        def config = new WhitelistedCommandConfig(
                ["nonexistent_binary_xyz"],
                60, 500, "test", null, null
        )
        def t = new WhitelistedCommandTool(config)

        when:
        def result = t.execute(["command": "nonexistent_binary_xyz --help"], context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("not installed or not on PATH")
    }

    def "extractBinary returns first token of matching prefix"() {
        expect:
        tool.extractBinary("df -h") == "df"
        tool.extractBinary("cat /proc/meminfo") == "cat"
    }

    def "extractBinary returns null for non-matching command"() {
        expect:
        tool.extractBinary("rm -rf /") == null
    }

    def "isBinaryAvailable returns true for common binaries"() {
        expect:
        tool.isBinaryAvailable("echo")
        tool.isBinaryAvailable("sh")
    }

    def "isBinaryAvailable returns false for nonexistent binary"() {
        expect:
        !tool.isBinaryAvailable("nonexistent_binary_xyz_123")
    }

    // --- Execution ---

    def "executes allowed command successfully"() {
        when:
        def result = tool.execute(["command": "echo hello"], context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().trim() == "hello"
    }

    def "respects maxOutputLines"() {
        given:
        def config = new WhitelistedCommandConfig(
                ["echo"],
                60,
                3,   // only capture 3 lines
                "test", null, null
        )
        def t = new WhitelistedCommandTool(config)

        when: "generate 10 lines of output"
        def result = t.execute(["command": 'echo "1\n2\n3\n4\n5\n6\n7\n8\n9\n10"'], context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("truncated")
    }

    def "reports missing command parameter"() {
        when:
        def result = tool.execute([:], context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Missing required parameter")
    }

    // --- ShowAllowedCommandsTool ---

    def "ShowAllowedCommandsTool returns prefix list with availability"() {
        given:
        def showConfig = new WhitelistedCommandConfig(
                ["echo", "df", "nonexistent_xyz_abc"],
                60, 500, "test", "Monitoring", null
        )
        def showTool = new ShowAllowedCommandsTool(showConfig)

        when:
        def result = showTool.execute([:], context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("# Allowed Commands")
        content.contains("Available")
        content.contains("echo")
        content.contains("df")
        content.contains("Not Installed")
        content.contains("nonexistent_xyz_abc")
        content.contains("NOT FOUND on PATH")
    }

    def "ShowAllowedCommandsTool marks known-good binaries as available"() {
        given:
        def showConfig = new WhitelistedCommandConfig(
                ["echo", "sh"],
                60, 500, "test", null, null
        )
        def showTool = new ShowAllowedCommandsTool(showConfig)

        when:
        def result = showTool.execute([:], context)

        then:
        result instanceof ToolResult.Success
        def content = (result as ToolResult.Success).content()
        content.contains("Available (2/2)")
        !content.contains("Not Installed")
    }

    def "ShowAllowedCommandsTool uses default tool name"() {
        given:
        def showTool = new ShowAllowedCommandsTool(defaultConfig)

        expect:
        showTool.definition().name() == "show_allowed_commands"
    }

    def "ShowAllowedCommandsTool uses custom tool name"() {
        given:
        def showTool = new ShowAllowedCommandsTool(defaultConfig, "my_show_commands")

        expect:
        showTool.definition().name() == "my_show_commands"
    }

    def "ShowAllowedCommandsTool uses section from config"() {
        given:
        def showTool = new ShowAllowedCommandsTool(defaultConfig)

        expect:
        showTool.definition().section() == "Monitoring"
    }
}
