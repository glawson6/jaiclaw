package io.jaiclaw.core.tool.param

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolDefinition
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

/**
 * 0.8.0 P3.2: locks the {@link TypedToolCallback} round-trip from raw
 * Map → typed record → ToolResult.
 */
class TypedToolCallbackSpec extends Specification {

    static record EchoParams(
            @ToolParameter(description = "the text to echo") String text,
            @ToolParameter(description = "number of repetitions", required = false) Integer count
    ) {}

    static class EchoTool implements TypedToolCallback<EchoParams> {
        @Override Class<EchoParams> parameterType() { EchoParams.class }
        @Override String toolName() { "echo" }
        @Override String toolDescription() { "echo back text" }

        @Override
        ToolResult execute(EchoParams params, ToolContext context) {
            int n = params.count() != null ? params.count() : 1
            return new ToolResult.Success((params.text() * n))
        }
    }

    def tool = new EchoTool()
    def ctx = new ToolContext("agent", "sess", "session-1", "/tmp")

    def "definition derives from the parameter record"() {
        when:
        ToolDefinition defn = tool.definition()

        then:
        defn.name() == "echo"
        defn.description() == "echo back text"
        defn.profiles().contains(ToolProfile.FULL)
        defn.inputSchema().contains('"text":{"type":"string"')
        defn.inputSchema().contains('"required":["text"]')
    }

    def "untyped Map execute path delegates to typed execute"() {
        when:
        ToolResult result = tool.execute([text: "hi", count: 3], ctx)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content() == "hihihi"
    }

    def "missing required parameter yields ToolResult.Error rather than throwing"() {
        when:
        ToolResult result = tool.execute([:], ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("text")
    }

    def "optional parameter defaults to null"() {
        when:
        ToolResult result = tool.execute([text: "x"], ctx)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content() == "x"
    }
}
