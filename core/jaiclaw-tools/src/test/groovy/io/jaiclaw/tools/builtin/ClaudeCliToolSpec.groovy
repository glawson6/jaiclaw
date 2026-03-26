package io.jaiclaw.tools.builtin

import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.tools.ToolCatalog
import spock.lang.Specification

class ClaudeCliToolSpec extends Specification {

    ClaudeCliTool tool = new ClaudeCliTool()

    // --- Tool definition ---

    def "tool name is claude_cli"() {
        expect:
        tool.definition().name() == "claude_cli"
    }

    def "tool section is Execution"() {
        expect:
        tool.definition().section() == ToolCatalog.SECTION_EXEC
    }

    def "tool is available in FULL profile only"() {
        expect:
        tool.definition().isAvailableIn(ToolProfile.FULL)
        !tool.definition().isAvailableIn(ToolProfile.MINIMAL)
        !tool.definition().isAvailableIn(ToolProfile.CODING)
        !tool.definition().isAvailableIn(ToolProfile.MESSAGING)
    }

    def "input schema contains prompt as required"() {
        given:
        def schema = tool.definition().inputSchema()

        expect:
        schema.contains('"prompt"')
        schema.contains('"required"')
        schema.contains('"prompt"')
    }

    def "STREAM_CONSUMER_KEY constant is defined"() {
        expect:
        ClaudeCliTool.STREAM_CONSUMER_KEY == "streamConsumer"
    }

    // --- buildCommand tests ---

    def "minimal command with only prompt"() {
        given:
        def params = [prompt: "hello world"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        cmd == ["claude", "-p", "--bare", "--no-session-persistence", "hello world"]
    }

    def "prompt is always the last argument"() {
        given:
        def params = [prompt: "the prompt", model: "opus"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        cmd.last() == "the prompt"
    }

    def "default flags include --bare and --no-session-persistence"() {
        given:
        def params = [prompt: "test"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        cmd.contains("--bare")
        cmd.contains("--no-session-persistence")
    }

    def "bare=false omits --bare"() {
        given:
        def params = [prompt: "test", bare: false]

        when:
        def cmd = tool.buildCommand(params)

        then:
        !cmd.contains("--bare")
        cmd.contains("--no-session-persistence")
    }

    def "continueSession adds --resume and omits --no-session-persistence"() {
        given:
        def params = [prompt: "test", continueSession: "abc-123"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        cmd.contains("--resume")
        cmd.contains("abc-123")
        !cmd.contains("--no-session-persistence")
    }

    def "model parameter adds --model flag"() {
        given:
        def params = [prompt: "test", model: "claude-sonnet-4-20250514"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def idx = cmd.indexOf("--model")
        idx >= 0
        cmd[idx + 1] == "claude-sonnet-4-20250514"
    }

    def "systemPrompt parameter adds --system-prompt flag"() {
        given:
        def params = [prompt: "test", systemPrompt: "You are a helpful assistant"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def idx = cmd.indexOf("--system-prompt")
        idx >= 0
        cmd[idx + 1] == "You are a helpful assistant"
    }

    def "maxTurns parameter adds --max-turns flag"() {
        given:
        def params = [prompt: "test", maxTurns: 5]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def idx = cmd.indexOf("--max-turns")
        idx >= 0
        cmd[idx + 1] == "5"
    }

    def "maxBudget parameter adds --max-budget-usd flag"() {
        given:
        def params = [prompt: "test", maxBudget: 1.50]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def idx = cmd.indexOf("--max-budget-usd")
        idx >= 0
        cmd[idx + 1] == "1.5" || cmd[idx + 1] == "1.50"
    }

    def "allowedTools CSV splits into multiple --allowedTools flags"() {
        given:
        def params = [prompt: "test", allowedTools: "Bash, Read, Write"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def indices = cmd.findIndexValues { it == "--allowedTools" }
        indices.size() == 3
        cmd[(indices[0] as int) + 1] == "Bash"
        cmd[(indices[1] as int) + 1] == "Read"
        cmd[(indices[2] as int) + 1] == "Write"
    }

    def "disallowedTools CSV splits into multiple --disallowedTools flags"() {
        given:
        def params = [prompt: "test", disallowedTools: "Edit,Write"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def indices = cmd.findIndexValues { it == "--disallowedTools" }
        indices.size() == 2
        cmd[(indices[0] as int) + 1] == "Edit"
        cmd[(indices[1] as int) + 1] == "Write"
    }

    def "effort parameter adds --effort flag"() {
        given:
        def params = [prompt: "test", effort: "high"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def idx = cmd.indexOf("--effort")
        idx >= 0
        cmd[idx + 1] == "high"
    }

    def "outputFormat parameter adds --output-format flag"() {
        given:
        def params = [prompt: "test", outputFormat: "json"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def idx = cmd.indexOf("--output-format")
        idx >= 0
        cmd[idx + 1] == "json"
    }

    def "all parameters combined"() {
        given:
        def params = [
            prompt: "do something",
            model: "opus",
            systemPrompt: "be helpful",
            maxTurns: 3,
            maxBudget: 2.0,
            allowedTools: "Bash",
            disallowedTools: "Edit",
            effort: "medium",
            outputFormat: "text",
            bare: true
        ]

        when:
        def cmd = tool.buildCommand(params)

        then:
        cmd.first() == "claude"
        cmd[1] == "-p"
        cmd.contains("--bare")
        cmd.contains("--no-session-persistence")
        cmd.contains("--model")
        cmd.contains("--system-prompt")
        cmd.contains("--max-turns")
        cmd.contains("--max-budget-usd")
        cmd.contains("--allowedTools")
        cmd.contains("--disallowedTools")
        cmd.contains("--effort")
        cmd.contains("--output-format")
        cmd.last() == "do something"
    }

    def "empty CSV items are skipped"() {
        given:
        def params = [prompt: "test", allowedTools: "Bash,,Read,"]

        when:
        def cmd = tool.buildCommand(params)

        then:
        def indices = cmd.findIndexValues { it == "--allowedTools" }
        indices.size() == 2
    }
}
