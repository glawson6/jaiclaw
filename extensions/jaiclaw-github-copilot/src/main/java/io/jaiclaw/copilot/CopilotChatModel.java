package io.jaiclaw.copilot;

import com.github.copilot.CopilotSession;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.ProviderConfig;
import com.github.copilot.rpc.SessionConfig;
import io.jaiclaw.copilot.tool.CopilotToolMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring AI {@link ChatModel} adapter over the official GitHub Copilot Java
 * SDK ({@code com.github:copilot-sdk-java}).
 *
 * <p>Wraps the SDK's session-based, event-driven API into Spring AI's
 * stateless {@code call(Prompt) -> ChatResponse} contract. Per-call sessions
 * are the default strategy (see {@code § 2.3a} of the module design plan) —
 * every call creates a fresh {@link CopilotSession} with the tools + system
 * message + model resolved from the incoming {@link Prompt}, blocks on
 * {@code sendAndWait(...)}, and closes the session before returning. This
 * keeps the model verifiably stateless from Spring AI's perspective and
 * matches the pattern every other Spring AI provider uses.
 *
 * <p><b>Model resolution</b> at call time follows the standard precedence:
 * per-call {@code prompt.getOptions().getModel()} → global default from the
 * {@link CopilotChatOptions} bean → {@code null} (SDK picks). See
 * {@link CopilotChatOptions#merge(CopilotChatOptions, CopilotChatOptions)}.
 *
 * <p><b>Tools</b> attached to the incoming Prompt via
 * {@link ToolCallingChatOptions#getToolCallbacks()} are wrapped by
 * {@link CopilotToolMapper} as Copilot {@code ToolDefinition}s whose
 * handlers delegate back into the Spring AI callbacks — this means tool
 * execution runs in-process (as Copilot's SDK expects) and Spring AI's own
 * tool-calling loop does <b>not</b> need to be involved. The response
 * returned from {@link #call(Prompt)} is the assistant's final message
 * <b>after</b> any tool round-trips have already happened server-side.
 *
 * <p><b>Streaming</b> is a light bridge in {@link #stream(Prompt)} that
 * delegates to {@link #call(Prompt)} and emits the resulting
 * {@link ChatResponse} as a single-element Flux. A true token-by-token
 * streaming implementation via {@code session.on(AssistantMessageDeltaEvent
 * .class, ...)} is a natural follow-up but out-of-scope for the initial
 * cut — the SDK's {@code sendAndWait} contract makes single-shot
 * request/response the safe first target.
 */
public class CopilotChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(CopilotChatModel.class);

    private final CopilotApi api;
    private final CopilotChatOptions defaultOptions;
    private final CopilotToolMapper toolMapper;
    /**
     * Optional provider-endpoint override — attached to every session's
     * {@code SessionConfig.setProvider(...)} when non-null. {@code null} means
     * "use Copilot's default server-side routing" — the safe default for the
     * vast majority of users. Non-null usage is for internal proxies,
     * OpenAI/Anthropic-compatible portals, or Azure deployments.
     */
    private final ProviderConfig providerOverride;

    public CopilotChatModel(CopilotApi api, CopilotChatOptions defaultOptions,
                            CopilotToolMapper toolMapper,
                            ProviderConfig providerOverride) {
        this.api = api;
        this.defaultOptions = defaultOptions == null ? new CopilotChatOptions() : defaultOptions;
        this.toolMapper = toolMapper == null ? new CopilotToolMapper() : toolMapper;
        this.providerOverride = providerOverride;
    }

    /**
     * Convenience overload — no provider override.
     */
    public CopilotChatModel(CopilotApi api, CopilotChatOptions defaultOptions,
                            CopilotToolMapper toolMapper) {
        this(api, defaultOptions, toolMapper, null);
    }

    /**
     * Convenience overload — no provider override, default mapper.
     */
    public CopilotChatModel(CopilotApi api, CopilotChatOptions defaultOptions) {
        this(api, defaultOptions, new CopilotToolMapper(), null);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return new CopilotChatOptions(defaultOptions);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        CopilotChatOptions effective = resolveOptions(prompt);
        SessionConfig cfg = buildSessionConfig(prompt, effective);

        CopilotSession session = api.createSession(cfg);
        try {
            String userPrompt = extractUserPromptText(prompt);
            MessageOptions msg = new MessageOptions().setPrompt(userPrompt);
            AssistantMessageEvent event;
            try {
                event = session.sendAndWait(msg).get();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Copilot sendAndWait failed: " + e.getMessage(), e);
            }
            return toChatResponse(event);
        } finally {
            closeQuietly(session);
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // Single-shot bridge — see class javadoc. Uses Mono.fromCallable
        // so the (blocking) call runs on Reactor's parallel scheduler, not
        // the caller's thread. Emits exactly one ChatResponse.
        return Mono.fromCallable(() -> call(prompt))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .flux();
    }

    // --- internals ---

    /**
     * Resolves the effective options for this call by merging the per-call
     * options (if the prompt carries a {@link CopilotChatOptions} instance)
     * with the model-level defaults. Prompts carrying a plain
     * {@link ChatOptions} (not our type) contribute only their
     * {@code getModel()} — other fields fall back to defaults.
     */
    CopilotChatOptions resolveOptions(Prompt prompt) {
        ChatOptions raw = prompt == null ? null : prompt.getOptions();
        CopilotChatOptions perCall;
        if (raw instanceof CopilotChatOptions cco) {
            perCall = cco;
        } else if (raw != null) {
            perCall = new CopilotChatOptions();
            perCall.setModel(raw.getModel());
            perCall.setTemperature(raw.getTemperature());
            perCall.setMaxTokens(raw.getMaxTokens());
            perCall.setTopP(raw.getTopP());
            perCall.setTopK(raw.getTopK());
            perCall.setFrequencyPenalty(raw.getFrequencyPenalty());
            perCall.setPresencePenalty(raw.getPresencePenalty());
            perCall.setStopSequences(raw.getStopSequences());
        } else {
            perCall = null;
        }
        return CopilotChatOptions.merge(perCall, defaultOptions);
    }

    /**
     * Builds the {@link SessionConfig} for this call — model, tools, system
     * message. Anything not mapped (temperature, penalties, etc.) simply
     * doesn't flow into the SDK; see {@link CopilotChatOptions}'s field
     * gap note.
     */
    SessionConfig buildSessionConfig(Prompt prompt, CopilotChatOptions effective) {
        SessionConfig cfg = new SessionConfig();
        if (effective.getModel() != null) {
            cfg.setModel(effective.getModel());
        }
        cfg.setClientName("jaiclaw-github-copilot");

        // System message from the Prompt, if any.
        String systemText = prompt == null ? null : extractSystemMessageText(prompt);
        if (systemText != null && !systemText.isBlank()) {
            com.github.copilot.rpc.SystemMessageConfig sysCfg =
                    new com.github.copilot.rpc.SystemMessageConfig();
            sysCfg.setContent(systemText);
            cfg.setSystemMessage(sysCfg);
        }

        // Tools from the Prompt's tool-calling options.
        List<ToolCallback> callbacks = extractToolCallbacks(prompt);
        if (!callbacks.isEmpty()) {
            cfg.setTools(toolMapper.toCopilotTools(callbacks));
        }

        // Provider-endpoint override — only attach if configured. Null means
        // "let Copilot's server-side routing pick the endpoint" (the default
        // for a normal Copilot user).
        if (providerOverride != null) {
            cfg.setProvider(providerOverride);
        }

        return cfg;
    }

    private List<ToolCallback> extractToolCallbacks(Prompt prompt) {
        if (prompt == null) {
            return Collections.emptyList();
        }
        ChatOptions opts = prompt.getOptions();
        if (opts instanceof ToolCallingChatOptions toolOpts) {
            List<ToolCallback> cbs = toolOpts.getToolCallbacks();
            return cbs == null ? Collections.emptyList() : cbs;
        }
        return Collections.emptyList();
    }

    private String extractUserPromptText(Prompt prompt) {
        if (prompt == null) {
            return "";
        }
        try {
            var userMessage = prompt.getUserMessage();
            if (userMessage != null) {
                String text = userMessage.getText();
                if (text != null) {
                    return text;
                }
            }
        } catch (Exception ignored) {
            // fall through to getContents()
        }
        String contents = prompt.getContents();
        return contents == null ? "" : contents;
    }

    private String extractSystemMessageText(Prompt prompt) {
        try {
            var sys = prompt.getSystemMessage();
            if (sys != null) {
                return sys.getText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Converts a Copilot {@link AssistantMessageEvent} into a Spring AI
     * {@link ChatResponse}. The assistant's content becomes the
     * {@link AssistantMessage#getText()}; any {@code toolRequests} on the
     * event become {@link AssistantMessage.ToolCall}s so downstream tool-loop
     * code can see them (even though Copilot has already executed the
     * in-process ones — see class javadoc). Model + token counts land in
     * {@link ChatGenerationMetadata}.
     */
    ChatResponse toChatResponse(AssistantMessageEvent event) {
        if (event == null || event.getData() == null) {
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage(""))));
        }
        var data = event.getData();
        String content = data.content() == null ? "" : data.content();

        List<AssistantMessage.ToolCall> toolCalls =
                toolMapper.toSpringAiToolCalls(data.toolRequests());

        AssistantMessage assistant = AssistantMessage.builder()
                .content(content)
                .toolCalls(toolCalls == null ? Collections.emptyList() : toolCalls)
                .build();

        Map<String, Object> genMetaProps = new LinkedHashMap<>();
        if (data.messageId() != null) genMetaProps.put("messageId", data.messageId());
        if (data.model() != null) genMetaProps.put("model", data.model());
        if (data.outputTokens() != null) genMetaProps.put("outputTokens", data.outputTokens());
        if (data.requestId() != null) genMetaProps.put("requestId", data.requestId());

        ChatGenerationMetadata genMeta = ChatGenerationMetadata.builder()
                .finishReason(deriveFinishReason(data))
                .metadata(genMetaProps)
                .build();

        Generation gen = new Generation(assistant, genMeta);

        ChatResponseMetadata respMeta = ChatResponseMetadata.builder()
                .model(data.model() == null ? "" : data.model())
                .build();

        return new ChatResponse(List.of(gen), respMeta);
    }

    /**
     * Derives a finish-reason string from the SDK's assistant-message data
     * fields. The SDK doesn't ship a single "finish_reason" field; we use
     * the presence of tool requests as the "TOOL_CALLS" signal (matching
     * OpenAI's finish reason naming) and fall back to "STOP" otherwise.
     */
    private String deriveFinishReason(
            com.github.copilot.generated.AssistantMessageEvent.AssistantMessageEventData data) {
        List<com.github.copilot.generated.AssistantMessageToolRequest> requests = data.toolRequests();
        if (requests != null && !requests.isEmpty()) {
            return "TOOL_CALLS";
        }
        return "STOP";
    }

    private void closeQuietly(CopilotSession session) {
        if (session == null) return;
        try {
            session.close();
        } catch (Exception e) {
            log.warn("Error closing Copilot session: {}", e.toString());
        }
    }
}
