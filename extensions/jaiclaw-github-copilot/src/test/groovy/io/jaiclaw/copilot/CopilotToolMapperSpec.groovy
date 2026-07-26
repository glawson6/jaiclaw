package io.jaiclaw.copilot

import com.github.copilot.generated.AssistantMessageToolRequest
import io.jaiclaw.copilot.tool.CopilotToolMapper
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.DefaultToolDefinition
import spock.lang.Specification

/**
 * Round-trips the tool-definition + tool-call mapping between Spring AI
 * types and Copilot SDK types. Verifies the load-bearing pieces of the
 * bridge without touching the SDK's network path.
 */
class CopilotToolMapperSpec extends Specification {

    CopilotToolMapper mapper = new CopilotToolMapper()

    // --- outbound: Spring AI ToolCallback -> Copilot ToolDefinition ---

    def "toCopilotTools wraps each Spring AI callback"() {
        given:
        ToolCallback cb1 = Mock()
        ToolCallback cb2 = Mock()
        cb1.getToolDefinition() >> DefaultToolDefinition.builder()
                .name("tool-one")
                .description("first")
                .inputSchema('{"type":"object","properties":{"a":{"type":"string"}}}')
                .build()
        cb2.getToolDefinition() >> DefaultToolDefinition.builder()
                .name("tool-two")
                .description("second")
                .inputSchema('{"type":"object","properties":{"b":{"type":"integer"}}}')
                .build()

        when:
        def result = mapper.toCopilotTools([cb1, cb2])

        then:
        result.size() == 2
        result[0].name() == "tool-one"
        result[0].description() == "first"
        result[0].parameters() instanceof Map
        (result[0].parameters() as Map).get("type") == "object"
        result[1].name() == "tool-two"
        (result[1].parameters() as Map).get("type") == "object"
    }

    def "toCopilotTools handles null and empty input"() {
        expect:
        mapper.toCopilotTools(null).isEmpty()
        mapper.toCopilotTools([]).isEmpty()
    }

    def "parseSchema falls back to empty map on malformed JSON"() {
        expect:
        mapper.parseSchema("not-a-json", "some-tool") == [:]
        mapper.parseSchema(null, "some-tool") == [:]
        mapper.parseSchema("", "some-tool") == [:]
    }

    def "parseSchema falls back to empty map on non-object JSON"() {
        expect:
        // JSON array is not a JSON object; should soft-fail per class javadoc
        mapper.parseSchema("[1, 2, 3]", "arr-tool") == [:]
    }

    def "outbound tool handler delegates back to the Spring AI callback"() {
        given:
        ToolCallback cb = Mock()
        cb.getToolDefinition() >> DefaultToolDefinition.builder()
                .name("greeter")
                .description("says hi")
                .inputSchema('{"type":"object","properties":{"name":{"type":"string"}}}')
                .build()
        cb.call(_ as String) >> { String args -> "hello from ${args}" }

        when:
        def copilotTool = mapper.toCopilotTool(cb)
        def invocation = new com.github.copilot.rpc.ToolInvocation()
                .setToolName("greeter")
                .setToolCallId("call-1")
        // ToolInvocation.getArguments() returns null unless setArguments is called
        // with a JsonNode; for this test we just verify the handler wiring runs
        // without an NPE and delegates.
        def resultFuture = copilotTool.handler().invoke(invocation)
        def result = resultFuture.get()

        then:
        result instanceof String
        (result as String).startsWith("hello from")
    }

    // --- inbound: Copilot event -> Spring AI ToolCall ---

    def "toSpringAiToolCall converts a Copilot tool request record"() {
        given:
        def request = new AssistantMessageToolRequest(
                "call-42",           // toolCallId
                "search_items",       // name
                [keyword: "spring"],  // arguments as a Map (SDK deserializes to Map/JsonNode)
                null,                 // type
                "Search Items",       // toolTitle
                null,                 // mcpServerName
                null,                 // mcpToolName
                null                  // intentionSummary
        )

        when:
        def tc = mapper.toSpringAiToolCall(request)

        then:
        tc.id() == "call-42"
        tc.type() == "function"
        tc.name() == "search_items"
        tc.arguments().contains('"keyword"')
        tc.arguments().contains('"spring"')
    }

    def "toSpringAiToolCall handles String arguments verbatim"() {
        given:
        def request = new AssistantMessageToolRequest(
                "call-1", "raw_json_tool", '{"already":"json"}',
                null, null, null, null, null)

        when:
        def tc = mapper.toSpringAiToolCall(request)

        then:
        tc.arguments() == '{"already":"json"}'
    }

    def "toSpringAiToolCall handles null arguments as empty object"() {
        given:
        def request = new AssistantMessageToolRequest(
                "call-1", "no_args_tool", null,
                null, null, null, null, null)

        when:
        def tc = mapper.toSpringAiToolCall(request)

        then:
        tc.arguments() == "{}"
    }

    def "toSpringAiToolCalls handles null list"() {
        expect:
        mapper.toSpringAiToolCalls(null).isEmpty()
        mapper.toSpringAiToolCalls([]).isEmpty()
    }

    def "toSpringAiToolCalls bulk-converts a list"() {
        given:
        def r1 = new AssistantMessageToolRequest(
                "c1", "t1", [x: 1], null, null, null, null, null)
        def r2 = new AssistantMessageToolRequest(
                "c2", "t2", [y: 2], null, null, null, null, null)

        when:
        def result = mapper.toSpringAiToolCalls([r1, r2])

        then:
        result.size() == 2
        result[0].id() == "c1"
        result[1].id() == "c2"
    }
}
