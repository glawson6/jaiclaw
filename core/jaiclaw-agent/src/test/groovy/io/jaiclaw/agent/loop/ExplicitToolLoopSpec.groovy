package io.jaiclaw.agent.loop

import io.jaiclaw.core.agent.*
import io.jaiclaw.core.hook.HookName
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.definition.DefaultToolDefinition
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class ExplicitToolLoopSpec extends Specification {

    ChatModel chatModel = Mock()
    AgentHookDispatcher hooks = Mock()
    ToolApprovalHandler approvalHandler = Mock()

    private AssistantMessage textMessage(String text) {
        AssistantMessage.builder().content(text).build()
    }

    private AssistantMessage toolCallMessage(List<AssistantMessage.ToolCall> toolCalls) {
        AssistantMessage.builder().content("").toolCalls(toolCalls).build()
    }

    private ToolCallback mockToolCallback(String name, String result) {
        def toolDef = DefaultToolDefinition.builder()
                .name(name).description("test").inputSchema('{"type":"object"}').build()
        Mock(ToolCallback) {
            getToolDefinition() >> toolDef
            call(_) >> result
        }
    }

    def "returns text response when no tool calls"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)
        def response = new ChatResponse(List.of(new Generation(textMessage("Hello there!"))))

        chatModel.call(_ as Prompt) >> response

        when:
        def result = loop.execute("system prompt", [], "hello", [:], "session-1")

        then:
        result.finalText() == "Hello there!"
        result.iterationsUsed() == 1
        result.history().isEmpty()
        result.durationMs() >= 0
    }

    def "executes tool calls and returns final text"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{"key":"val"}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def textResponse = new ChatResponse(List.of(new Generation(textMessage("Done!"))))

        chatModel.call(_ as Prompt) >>> [toolResponse, textResponse]

        def mockTool = mockToolCallback("myTool", "tool result")

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "sess-1")

        then:
        result.finalText() == "Done!"
        result.iterationsUsed() == 2
        result.history().size() == 1
        result.history()[0].toolName() == "myTool"
        result.history()[0].result() == "tool result"
        result.durationMs() >= 0
    }

    def "fires BEFORE and AFTER tool call hooks"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def textResponse = new ChatResponse(List.of(new Generation(textMessage("Done"))))

        chatModel.call(_ as Prompt) >>> [toolResponse, textResponse]

        def mockTool = mockToolCallback("myTool", "result")

        when:
        loop.execute("system", [], "input", ["myTool": mockTool], "sess-1")

        then:
        1 * hooks.fireVoid(HookName.BEFORE_TOOL_CALL, _ as ToolCallEvent, "sess-1")
        1 * hooks.fireVoid(HookName.AFTER_TOOL_CALL, _ as ToolCallEvent, "sess-1")
    }

    def "stops at max iterations"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 2, false)
        def loop = new ExplicitToolLoop(chatModel, config, null, null)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))

        chatModel.call(_ as Prompt) >> toolResponse

        def mockTool = mockToolCallback("myTool", "result")

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "sess-1")

        then:
        result.finalText().contains("Max iterations reached")
        result.iterationsUsed() == 2
        result.durationMs() >= 0
    }

    def "approval denial stops tool execution"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, true)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, approvalHandler)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def textResponse = new ChatResponse(List.of(new Generation(textMessage("OK denied"))))

        chatModel.call(_ as Prompt) >>> [toolResponse, textResponse]

        approvalHandler.requestApproval("myTool", _, "sess-1") >>
                CompletableFuture.completedFuture(new ToolApprovalDecision.Denied("not allowed"))

        def toolDef = DefaultToolDefinition.builder()
                .name("myTool").description("test").inputSchema('{"type":"object"}').build()
        def mockTool = Mock(ToolCallback) {
            getToolDefinition() >> toolDef
        }

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "sess-1")

        then:
        result.history().size() == 1
        result.history()[0].result().contains("denied")
        0 * mockTool.call(_)
    }

    def "approval proceeds when approved"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, true)
        def loop = new ExplicitToolLoop(chatModel, config, null, approvalHandler)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def textResponse = new ChatResponse(List.of(new Generation(textMessage("Done"))))

        chatModel.call(_ as Prompt) >>> [toolResponse, textResponse]

        approvalHandler.requestApproval("myTool", _, "sess-1") >>
                CompletableFuture.completedFuture(new ToolApprovalDecision.Approved())

        def mockTool = mockToolCallback("myTool", "approved result")

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "sess-1")

        then:
        result.finalText() == "Done"
        result.history()[0].result() == "approved result"
    }
}
