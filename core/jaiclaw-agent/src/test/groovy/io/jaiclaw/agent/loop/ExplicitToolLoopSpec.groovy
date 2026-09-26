package io.jaiclaw.agent.loop

import io.jaiclaw.core.agent.*
import io.jaiclaw.core.hook.event.ToolCallEndedEvent
import io.jaiclaw.core.hook.event.ToolCallStartedEvent
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
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
        def result = loop.execute("system prompt", [], "hello", [:], "default", "session-1")

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
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

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
        loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

        then:
        1 * hooks.fireVoid(_ as ToolCallStartedEvent)
        1 * hooks.fireVoid(_ as ToolCallEndedEvent)
    }

    def "exhausting the budget ends with a tool-less final turn"() {
        given: "a 2-iteration budget and a model that keeps asking for the same tool"
        // Repetition threshold is disabled here so the budget, not the repetition
        // guard, is what ends the run.
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 2, false,
                null, ToolLoopConfig.DEFAULT_WARNING_RATIO, 0, [:])
        def loop = new ExplicitToolLoop(chatModel, config, null, null)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def summary = new ChatResponse(List.of(new Generation(textMessage("Here is what I found."))))

        // Two tool-calling turns, then the tool-less final turn returns prose.
        chatModel.call(_ as Prompt) >>> [toolResponse, toolResponse, summary]

        def mockTool = mockToolCallback("myTool", "result")

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

        then: "the run answers rather than returning a placeholder"
        result.finalText() == "Here is what I found."
        result.iterationsUsed() == 2
        result.durationMs() >= 0
    }

    def "budget exhaustion falls back to the guard instruction when the final turn yields no prose"() {
        given: "a model that only ever emits tool calls, even on the final turn"
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 2, false,
                null, ToolLoopConfig.DEFAULT_WARNING_RATIO, 0, [:])
        def loop = new ExplicitToolLoop(chatModel, config, null, null)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        chatModel.call(_ as Prompt) >> toolResponse

        def mockTool = mockToolCallback("myTool", "result")

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

        then: "the caller still gets a coherent explanation, never an empty string"
        result.finalText().contains("budget")
        result.iterationsUsed() == 2
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
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

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
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

        then:
        result.finalText() == "Done"
        result.history()[0].result() == "approved result"
    }

    // --- Regression: the options handed to ChatModel.call() must preserve the
    // provider's concrete ChatOptions type.
    //
    // Spring AI 2.0 provider models narrow the prompt options to their own type and
    // discard anything else. AnthropicChatModel.createRequest is the reference case:
    //
    //     options instanceof AnthropicChatOptions ? (AnthropicChatOptions) options
    //                                             : AnthropicChatOptions.builder().build()
    //
    // and then reads BOTH getModel() and the tool list back off that object. Passing a
    // generic ToolCallingChatOptions therefore shipped requests with no tools and the
    // provider's fallback model — the shipped 1.2.0 defect.

    def "tool options are derived from the model's own defaults, preserving type and fields"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)
        def mockTool = mockToolCallback("myTool", "tool result")

        // Stands in for a provider's configured options (AnthropicChatOptions etc.):
        // a tool-capable options object carrying the model name and limits.
        def providerDefaults = ToolCallingChatOptions.builder()
                .model("claude-sonnet-4-5")
                .maxTokens(4096)
                .temperature(0.2d)
                .build()

        Prompt captured = null

        when:
        loop.execute("system prompt", [], "hello", ["myTool": mockTool], "default", "session-1")

        then: "the loop asks the model for its defaults rather than building generic options"
        1 * chatModel.getDefaultOptions() >> providerDefaults
        1 * chatModel.call(_ as Prompt) >> { Prompt p ->
            captured = p
            new ChatResponse(List.of(new Generation(textMessage("Done!"))))
        }

        and: "the concrete options type is preserved — this is what the provider's instanceof check needs"
        captured.options.getClass() == providerDefaults.getClass()

        and: "provider-configured fields survive instead of being replaced by empty defaults"
        captured.options.model == "claude-sonnet-4-5"
        captured.options.maxTokens == 4096
        captured.options.temperature == 0.2d

        and: "the tools are actually attached"
        captured.options.toolCallbacks*.toolDefinition*.name == ["myTool"]
    }

    def "falls back to a generic builder when model defaults are not tool-capable"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)
        def mockTool = mockToolCallback("myTool", "tool result")

        Prompt captured = null

        when:
        loop.execute("system prompt", [], "hello", ["myTool": mockTool], "default", "session-1")

        then:
        // A plain ChatOptions is not a ToolCallingChatOptions — no mutate() path.
        1 * chatModel.getDefaultOptions() >> ChatOptions.builder().model("some-model").build()
        1 * chatModel.call(_ as Prompt) >> { Prompt p ->
            captured = p
            new ChatResponse(List.of(new Generation(textMessage("Done!"))))
        }

        and: "tools still get attached rather than throwing"
        captured.options instanceof ToolCallingChatOptions
        captured.options.toolCallbacks*.toolDefinition*.name == ["myTool"]
    }

    // --- Regression: the approval gate must fail CLOSED.
    //
    // Previously the gate was `if (approvalRequired && approvalHandler != null)`, so a
    // deployment with no ToolApprovalHandler bean executed approval-required tools
    // without any gate at all. Headless deployments register no handler by default,
    // which made PROMPT_ALWAYS *looser* than DENY and contradicted the documented
    // ApprovalFloor contract ("a floor can only make approval stricter, never looser").
    // In that configuration the only remaining gate was the system prompt, which a
    // model can be talked past.

    def "PROMPT_ALWAYS floor denies when no approval handler is registered"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false,
                null, ToolLoopConfig.DEFAULT_WARNING_RATIO, ToolLoopConfig.DEFAULT_REPETITION_THRESHOLD,
                ["rebootDevice": ApprovalFloor.PROMPT_ALWAYS])
        // No handler — the headless default.
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "rebootDevice", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def textResponse = new ChatResponse(List.of(new Generation(textMessage("understood"))))
        chatModel.call(_ as Prompt) >>> [toolResponse, textResponse]

        def toolDef = DefaultToolDefinition.builder()
                .name("rebootDevice").description("test").inputSchema('{"type":"object"}').build()
        def mockTool = Mock(ToolCallback) { getToolDefinition() >> toolDef }

        when:
        def result = loop.execute("system", [], "reboot it", ["rebootDevice": mockTool], "default", "sess-1")

        then: "the tool never runs"
        0 * mockTool.call(_)

        and: "and the model is told why, so it can choose another path"
        result.history().size() == 1
        result.history()[0].result().contains("denied")
        result.history()[0].result().contains("no approval handler is configured")
    }

    def "requireApproval denies when no approval handler is registered"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, true)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def textResponse = new ChatResponse(List.of(new Generation(textMessage("understood"))))
        chatModel.call(_ as Prompt) >>> [toolResponse, textResponse]

        def toolDef = DefaultToolDefinition.builder()
                .name("myTool").description("test").inputSchema('{"type":"object"}').build()
        def mockTool = Mock(ToolCallback) { getToolDefinition() >> toolDef }

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

        then:
        0 * mockTool.call(_)
        result.history()[0].result().contains("denied")
    }

    def "a throwing approval handler denies rather than falling through to execution"() {
        given:
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, true)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, approvalHandler)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def textResponse = new ChatResponse(List.of(new Generation(textMessage("understood"))))
        chatModel.call(_ as Prompt) >>> [toolResponse, textResponse]

        // A handler that blows up must not be read as an approval.
        approvalHandler.requestApproval("myTool", _, "sess-1") >> { throw new IllegalStateException("approval backend down") }

        def toolDef = DefaultToolDefinition.builder()
                .name("myTool").description("test").inputSchema('{"type":"object"}').build()
        def mockTool = Mock(ToolCallback) { getToolDefinition() >> toolDef }

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

        then:
        0 * mockTool.call(_)
        result.history()[0].result().contains("approval could not be obtained")
    }

    def "no handler is harmless when the tool requires no approval"() {
        given: "the default posture — no approval required, no handler registered"
        def config = new ToolLoopConfig(ToolLoopConfig.Mode.EXPLICIT, 10, false)
        def loop = new ExplicitToolLoop(chatModel, config, hooks, null)

        def toolCall = new AssistantMessage.ToolCall("tc-1", "function", "myTool", '{}')
        def toolResponse = new ChatResponse(List.of(new Generation(toolCallMessage([toolCall]))))
        def textResponse = new ChatResponse(List.of(new Generation(textMessage("Done"))))
        chatModel.call(_ as Prompt) >>> [toolResponse, textResponse]

        def mockTool = mockToolCallback("myTool", "tool result")

        when:
        def result = loop.execute("system", [], "input", ["myTool": mockTool], "default", "sess-1")

        then: "the fail-closed check must not affect ordinary tools"
        result.finalText() == "Done"
        result.history()[0].result() == "tool result"
    }
}
