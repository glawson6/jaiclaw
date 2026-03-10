package io.jclaw.tools.bridge

import io.jclaw.core.tool.ToolCallback
import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolDefinition
import io.jclaw.core.tool.ToolProfile
import io.jclaw.core.tool.ToolResult
import spock.lang.Specification

class SpringAiToolBridgeSpec extends Specification {

    def defaultContext = new ToolContext("agent1", "session1", "sid1", "/workspace")

    def "bridge exposes Spring AI ToolDefinition with correct name and description"() {
        given:
        def jclawTool = createTool("test_tool", "A test tool", '{"type":"object","properties":{},"required":[]}')
        def bridge = new SpringAiToolBridge(jclawTool, defaultContext)

        when:
        def springDef = bridge.getToolDefinition()

        then:
        springDef.name() == "test_tool"
        springDef.description() == "A test tool"
        springDef.inputSchema() == '{"type":"object","properties":{},"required":[]}'
    }

    def "call with JSON input deserializes parameters and invokes JClaw tool"() {
        given:
        Map<String, Object> capturedParams = null
        def jclawTool = createTool("echo", "Echo tool", '{"type":"object","properties":{"msg":{"type":"string"}},"required":["msg"]}') { params, ctx ->
            capturedParams = params
            return new ToolResult.Success("echoed: " + params.get("msg"))
        }
        def bridge = new SpringAiToolBridge(jclawTool, defaultContext)

        when:
        def result = bridge.call('{"msg":"hello"}')

        then:
        result == "echoed: hello"
        capturedParams != null
        capturedParams["msg"] == "hello"
    }

    def "call with null input passes empty map"() {
        given:
        Map<String, Object> capturedParams = null
        def jclawTool = createTool("noop", "No-op", '{"type":"object","properties":{},"required":[]}') { params, ctx ->
            capturedParams = params
            return new ToolResult.Success("done")
        }
        def bridge = new SpringAiToolBridge(jclawTool, defaultContext)

        when:
        def result = bridge.call(null)

        then:
        result == "done"
        capturedParams != null
        capturedParams.isEmpty()
    }

    def "call returns ERROR prefix for ToolResult.Error"() {
        given:
        def jclawTool = createTool("fail", "Failing tool", '{"type":"object","properties":{},"required":[]}') { params, ctx ->
            return new ToolResult.Error("something broke")
        }
        def bridge = new SpringAiToolBridge(jclawTool, defaultContext)

        when:
        def result = bridge.call('{}')

        then:
        result == "ERROR: something broke"
    }

    def "call returns ERROR for malformed JSON input"() {
        given:
        def jclawTool = createTool("strict", "Strict", '{"type":"object","properties":{},"required":[]}') { params, ctx ->
            return new ToolResult.Success("should not reach")
        }
        def bridge = new SpringAiToolBridge(jclawTool, defaultContext)

        when:
        def result = bridge.call('not valid json{{{')

        then:
        result.startsWith("ERROR:")
    }

    def "call propagates default context to JClaw tool"() {
        given:
        ToolContext capturedContext = null
        def jclawTool = createTool("ctx", "Context tool", '{"type":"object","properties":{},"required":[]}') { params, ctx ->
            capturedContext = ctx
            return new ToolResult.Success("ok")
        }
        def bridge = new SpringAiToolBridge(jclawTool, defaultContext)

        when:
        bridge.call('{}')

        then:
        capturedContext != null
        capturedContext.agentId() == "agent1"
        capturedContext.sessionKey() == "session1"
        capturedContext.workspaceDir() == "/workspace"
    }

    def "unwrap returns original JClaw tool"() {
        given:
        def jclawTool = createTool("orig", "Original", '{"type":"object","properties":{},"required":[]}')
        def bridge = new SpringAiToolBridge(jclawTool, defaultContext)

        expect:
        bridge.unwrap().is(jclawTool)
    }

    def "bridgeAll creates bridges for multiple tools"() {
        given:
        def tools = [
            createTool("a", "A", '{"type":"object","properties":{},"required":[]}'),
            createTool("b", "B", '{"type":"object","properties":{},"required":[]}'),
        ]

        when:
        def bridges = SpringAiToolBridge.bridgeAll(tools, defaultContext)

        then:
        bridges.size() == 2
        bridges.every { it instanceof org.springframework.ai.tool.ToolCallback }
    }

    private ToolCallback createTool(String name, String desc, String schema,
                                     Closure<ToolResult> executor = { p, c -> new ToolResult.Success("default") }) {
        def definition = new ToolDefinition(name, desc, "Test", schema, Set.of(ToolProfile.FULL))
        return new ToolCallback() {
            @Override
            ToolDefinition definition() { return definition }

            @Override
            ToolResult execute(Map<String, Object> parameters, ToolContext context) {
                return executor.call(parameters, context)
            }
        }
    }
}
