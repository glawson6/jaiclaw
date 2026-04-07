package io.jaiclaw.agent.loop;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.agent.LlmTraceLogger;
import io.jaiclaw.core.agent.*;
import io.jaiclaw.core.hook.HookName;
import io.jaiclaw.core.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Manages an explicit tool call loop using {@link ChatModel#call(Prompt)} directly
 * with internal tool execution disabled. This provides step-level hook observability,
 * optional human-in-the-loop approval, and iteration capping.
 */
public class ExplicitToolLoop {

    private static final Logger log = LoggerFactory.getLogger(ExplicitToolLoop.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel chatModel;
    private final ToolLoopConfig config;
    private final AgentHookDispatcher hooks;
    private final ToolApprovalHandler approvalHandler;

    public record LoopResult(String finalText, List<ToolCallEvent> history, int iterationsUsed, TokenUsage totalUsage, long durationMs) {}

    public ExplicitToolLoop(ChatModel chatModel, ToolLoopConfig config,
                            AgentHookDispatcher hooks, ToolApprovalHandler approvalHandler) {
        this.chatModel = chatModel;
        this.config = config;
        this.hooks = hooks;
        this.approvalHandler = approvalHandler;
    }

    public LoopResult execute(String systemPrompt, List<Message> history,
                              String userInput, Map<String, ToolCallback> toolsByName,
                              String sessionKey) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.addAll(history);
        messages.add(new UserMessage(userInput));

        List<ToolCallEvent> toolCallHistory = new ArrayList<>();
        TokenUsage accumulatedUsage = TokenUsage.ZERO;
        long loopStartNanos = System.nanoTime();

        for (int i = 0; i < config.maxIterations(); i++) {
            var options = ToolCallingChatOptions.builder()
                    .internalToolExecutionEnabled(false)
                    .toolCallbacks(new ArrayList<>(toolsByName.values()))
                    .build();

            long iterStartNanos = System.nanoTime();
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            long iterMs = (System.nanoTime() - iterStartNanos) / 1_000_000;
            log.debug("Explicit loop iteration {} — {} ms", i + 1, iterMs);
            var output = response.getResult().getOutput();

            TokenUsage iterationUsage = extractUsage(response);
            accumulatedUsage = accumulatedUsage.add(iterationUsage);

            LlmTraceLogger.logIteration(i + 1, messages, output.getText(),
                    toolsByName.values(), iterationUsage.inputTokens(), iterationUsage.outputTokens());

            if (output.getToolCalls() == null || output.getToolCalls().isEmpty()) {
                long durationMs = (System.nanoTime() - loopStartNanos) / 1_000_000;
                return new LoopResult(output.getText(), toolCallHistory, i + 1, accumulatedUsage, durationMs);
            }

            // Add the assistant message with tool calls to conversation
            messages.add(output);

            // Execute each tool call with hooks + optional approval
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            for (var tc : output.getToolCalls()) {
                int iteration = i + 1;

                // Fire BEFORE_TOOL_CALL hook
                var beforeEvent = ToolCallEvent.before(tc.name(), tc.arguments(), iteration, sessionKey);
                if (hooks != null) {
                    hooks.fireVoid(HookName.BEFORE_TOOL_CALL, beforeEvent, sessionKey);
                }

                // Optional approval gate
                String toolArguments = tc.arguments();
                if (config.requireApproval() && approvalHandler != null) {
                    try {
                        Map<String, Object> params = parseParams(toolArguments);
                        var decision = approvalHandler.requestApproval(tc.name(), params, sessionKey).get();
                        switch (decision) {
                            case ToolApprovalDecision.Approved a -> { /* proceed */ }
                            case ToolApprovalDecision.Denied d -> {
                                String denialResult = "Tool call denied: " + d.reason();
                                responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), denialResult));
                                var afterEvent = ToolCallEvent.after(tc.name(), toolArguments, denialResult, iteration, sessionKey);
                                toolCallHistory.add(afterEvent);
                                if (hooks != null) {
                                    hooks.fireVoid(HookName.AFTER_TOOL_CALL, afterEvent, sessionKey);
                                }
                                continue;
                            }
                            case ToolApprovalDecision.Modified m -> {
                                toolArguments = MAPPER.writeValueAsString(m.parameters());
                            }
                        }
                    } catch (ExecutionException | InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Approval request interrupted for tool {}", tc.name(), e);
                        String errorResult = "Tool call approval interrupted";
                        responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), errorResult));
                        continue;
                    } catch (Exception e) {
                        log.warn("Approval handling failed for tool {}", tc.name(), e);
                    }
                }

                // Execute the tool
                String result;
                ToolCallback callback = toolsByName.get(tc.name());
                if (callback != null) {
                    try {
                        result = callback.call(toolArguments);
                    } catch (Exception e) {
                        log.error("Tool execution failed: {}", tc.name(), e);
                        result = "ERROR: " + e.getMessage();
                    }
                } else {
                    result = "ERROR: Unknown tool: " + tc.name();
                }

                responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));

                // Fire AFTER_TOOL_CALL hook
                var afterEvent = ToolCallEvent.after(tc.name(), toolArguments, result, iteration, sessionKey);
                toolCallHistory.add(afterEvent);
                if (hooks != null) {
                    hooks.fireVoid(HookName.AFTER_TOOL_CALL, afterEvent, sessionKey);
                }
            }

            messages.add(ToolResponseMessage.builder().responses(responses).build());
        }

        log.warn("Explicit tool loop hit max iterations ({}) for session {}", config.maxIterations(), sessionKey);
        long durationMs = (System.nanoTime() - loopStartNanos) / 1_000_000;
        return new LoopResult("Max iterations reached (" + config.maxIterations() + ")", toolCallHistory, config.maxIterations(), accumulatedUsage, durationMs);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public static TokenUsage extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) return TokenUsage.ZERO;
        var usage = response.getMetadata().getUsage();
        if (usage == null) return TokenUsage.ZERO;

        int input = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int output = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

        int cacheRead = 0;
        int cacheWrite = 0;
        Object nativeUsage = usage.getNativeUsage();
        if (nativeUsage != null) {
            try {
                // Use reflection to avoid compile-time dependency on spring-ai-anthropic
                var cacheReadMethod = nativeUsage.getClass().getMethod("cacheReadInputTokens");
                var cacheWriteMethod = nativeUsage.getClass().getMethod("cacheCreationInputTokens");
                Integer cr = (Integer) cacheReadMethod.invoke(nativeUsage);
                Integer cw = (Integer) cacheWriteMethod.invoke(nativeUsage);
                if (cr != null) cacheRead = cr;
                if (cw != null) cacheWrite = cw;
            } catch (NoSuchMethodException e) {
                // Not an Anthropic response — no cache tokens
            } catch (Exception e) {
                log.debug("Failed to extract cache token usage", e);
            }
        }

        return new TokenUsage(input, output, cacheRead, cacheWrite);
    }
}
