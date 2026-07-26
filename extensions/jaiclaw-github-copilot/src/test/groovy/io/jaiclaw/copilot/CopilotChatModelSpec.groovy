package io.jaiclaw.copilot

import com.github.copilot.generated.AssistantMessageEvent
import com.github.copilot.rpc.SessionConfig
import io.jaiclaw.copilot.tool.CopilotToolMapper
import org.springframework.ai.chat.prompt.Prompt
import spock.lang.Specification

/**
 * Unit specs for {@link CopilotChatModel}'s option merge and response
 * conversion — the pieces that don't touch the SDK's transport.
 */
class CopilotChatModelSpec extends Specification {

    CopilotApi api = Stub()
    CopilotToolMapper mapper = new CopilotToolMapper()

    def "CopilotModel enum round-trips API names"() {
        expect:
        CopilotModel.CLAUDE_3_5_SONNET.toApiName() == "claude-3.5-sonnet"
        CopilotModel.GPT_4O.toApiName() == "gpt-4o"
        CopilotModel.fromApiName("claude-3.5-sonnet") == CopilotModel.CLAUDE_3_5_SONNET
        CopilotModel.fromApiName(null) == null
        CopilotModel.fromApiName("model-that-does-not-exist") == null
    }

    def "CopilotChatOptions builder produces a Copilot options instance"() {
        when:
        def opts = CopilotChatOptions.builder()
                .model(CopilotModel.CLAUDE_3_5_SONNET)
                .temperature(0.5d)
                .maxTokens(1024)
                .build()

        then:
        opts instanceof CopilotChatOptions
        opts.getModel() == "claude-3.5-sonnet"
        opts.getTemperature() == 0.5d
        opts.getMaxTokens() == 1024
    }

    def "merge falls per-call → default → null"() {
        given:
        def defaults = new CopilotChatOptions(
                model: "claude-3.5-sonnet",
                temperature: 0.7d)
        def perCall = new CopilotChatOptions(model: "gpt-5")

        when:
        def merged = CopilotChatOptions.merge(perCall, defaults)

        then:
        merged.getModel() == "gpt-5"          // per-call wins
        merged.getTemperature() == 0.7d       // falls back to default
    }

    def "merge treats null per-call as identity"() {
        given:
        def defaults = new CopilotChatOptions(model: "claude-3.5-sonnet")

        when:
        def merged = CopilotChatOptions.merge(null, defaults)

        then:
        merged.getModel() == "claude-3.5-sonnet"
    }

    def "resolveOptions merges per-call CopilotChatOptions onto defaults"() {
        given:
        def defaults = CopilotChatOptions.builder()
                .model(CopilotModel.CLAUDE_3_5_SONNET)
                .temperature(0.7d)
                .build()
        def chatModel = new CopilotChatModel(api, defaults, mapper)
        def perCall = CopilotChatOptions.builder().model(CopilotModel.GPT_5).build()
        def prompt = new Prompt("hello", perCall)

        when:
        def resolved = chatModel.resolveOptions(prompt)

        then:
        resolved.getModel() == "gpt-5"        // per-call
        resolved.getTemperature() == 0.7d     // default fallback
    }

    def "buildSessionConfig sets model + client name"() {
        given:
        def defaults = CopilotChatOptions.builder()
                .model(CopilotModel.CLAUDE_3_5_SONNET)
                .build()
        def chatModel = new CopilotChatModel(api, defaults, mapper)
        def prompt = new Prompt("what is 2+2?")

        when:
        def cfg = chatModel.buildSessionConfig(prompt, chatModel.resolveOptions(prompt))

        then:
        cfg instanceof SessionConfig
        cfg.getModel() == "claude-3.5-sonnet"
        cfg.getClientName() == "jaiclaw-github-copilot"
        cfg.getProvider() == null   // no override configured
    }

    def "buildSessionConfig attaches provider override when configured"() {
        given:
        def defaults = CopilotChatOptions.builder()
                .model(CopilotModel.CLAUDE_3_5_SONNET).build()
        def providerOverride = new com.github.copilot.rpc.ProviderConfig()
                .setType("anthropic")
                .setBaseUrl("https://api.minimax.io/anthropic")
        def chatModel = new CopilotChatModel(api, defaults, mapper, providerOverride)
        def prompt = new Prompt("hello")

        when:
        def cfg = chatModel.buildSessionConfig(prompt, chatModel.resolveOptions(prompt))

        then:
        cfg.getProvider() != null
        cfg.getProvider().getBaseUrl() == "https://api.minimax.io/anthropic"
        cfg.getProvider().getType() == "anthropic"
    }

    def "toChatResponse maps content + finish reason"() {
        given:
        def chatModel = new CopilotChatModel(api, new CopilotChatOptions(), mapper)
        def data = new AssistantMessageEvent.AssistantMessageEventData(
                "msg-1",       // messageId
                "gpt-4o",       // model
                "the answer is 4",  // content
                null,           // toolRequests
                null, null, null, null, null,   // reasoning + encryptedContent + phase
                123L,           // outputTokens
                "int-1", "req-1", "srvreq-1", "api-1",  // interactionId etc
                null,           // serverTools
                "turn-1", null, // turnId, parentToolCallId
                null            // citations
        )
        def event = new AssistantMessageEvent()
        event.setData(data)

        when:
        def response = chatModel.toChatResponse(event)

        then:
        response.getResults().size() == 1
        response.getResult().getOutput().getText() == "the answer is 4"
        response.getResult().getMetadata().getFinishReason() == "STOP"
        response.getMetadata().getModel() == "gpt-4o"
    }

    def "toChatResponse maps toolRequests to finish reason TOOL_CALLS"() {
        given:
        def chatModel = new CopilotChatModel(api, new CopilotChatOptions(), mapper)
        def toolReq = new com.github.copilot.generated.AssistantMessageToolRequest(
                "call-1", "search", [q: "hi"], null, null, null, null, null)
        def data = new AssistantMessageEvent.AssistantMessageEventData(
                "msg-1", "gpt-4o", "calling search", [toolReq],
                null, null, null, null, null,
                42L, "int-1", "req-1", "srvreq-1", "api-1",
                null, "turn-1", null, null)
        def event = new AssistantMessageEvent()
        event.setData(data)

        when:
        def response = chatModel.toChatResponse(event)

        then:
        response.getResult().getMetadata().getFinishReason() == "TOOL_CALLS"
        response.getResult().getOutput().getToolCalls().size() == 1
        response.getResult().getOutput().getToolCalls()[0].id() == "call-1"
        response.getResult().getOutput().getToolCalls()[0].name() == "search"
    }
}
