package io.jaiclaw.agent.loop;

import tools.jackson.databind.ObjectMapper;
import io.jaiclaw.agent.LlmTraceLogger;
import io.jaiclaw.core.agent.*;
import io.jaiclaw.core.hook.event.ToolCallEndedEvent;
import io.jaiclaw.core.hook.event.ToolCallStartedEvent;
import io.jaiclaw.core.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
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

    /**
     * No-media overload — delegates with an empty media list. Kept so existing
     * callers (and the public signature surface) don't need to know about media.
     */
    public LoopResult execute(String systemPrompt, List<Message> history,
                              String userInput, Map<String, ToolCallback> toolsByName,
                              String agentId, String sessionKey) {
        return execute(systemPrompt, history, userInput, List.of(),
                toolsByName, agentId, sessionKey);
    }

    /**
     * Media-aware execution. When {@code media} is non-empty the user message
     * is built via {@link UserMessage#builder()} so the media content blocks
     * land on the prompt; otherwise the string-only constructor is used (a
     * tiny optimisation that also keeps the trace logs simpler).
     */
    public LoopResult execute(String systemPrompt, List<Message> history,
                              String userInput, List<Media> media,
                              Map<String, ToolCallback> toolsByName,
                              String agentId, String sessionKey) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.addAll(history);
        if (media == null || media.isEmpty()) {
            messages.add(new UserMessage(userInput));
        } else {
            messages.add(UserMessage.builder().text(userInput).media(media).build());
        }

        List<ToolCallEvent> toolCallHistory = new ArrayList<>();
        TokenUsage accumulatedUsage = TokenUsage.ZERO;
        long loopStartNanos = System.nanoTime();

        // Phase 1 runtime guards: iteration budget, repetition, empty responses,
        // approval floors. Neutral unless configured — see LoopGuard.
        LoopGuard guard = new LoopGuard(config, hooks, agentId, sessionKey);

        for (int i = 0; i < config.maxIterations(); i++) {
            // Budget exhausted, or a guard asked us to stop using tools: make one
            // final call with no tools attached so the run still answers.
            if (!guard.tryConsumeIteration() || guard.finalTurnRequested()) {
                String instruction = guard.finalTurnRequested()
                        ? guard.finalTurnInstruction()
                        : BudgetGuard.exhaustedInstruction();
                return finalTurn(messages, instruction, toolCallHistory,
                        accumulatedUsage, guard.iterationsUsed(), loopStartNanos);
            }

            // Spring AI 2.0: the internalToolExecutionEnabled(false) flag from 1.x was removed.
            // Instead, the ChatModel only auto-executes tools when a caller-supplied
            // ToolCallingManager is wired into the ChatModel's construction. Our
            // ChatModel beans are built without a manager, so ChatModel.call() returns
            // the tool-call requests without executing them — which is exactly what this
            // loop needs. We continue to run each ToolCallback ourselves below, preserving
            // the BEFORE/AFTER hook points + optional approval gate + per-iteration
            // accounting that this class exists to provide.
            var options = ToolCallingChatOptions.builder()
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

            int requestedToolCalls = output.getToolCalls() == null ? 0 : output.getToolCalls().size();
            boolean forceAfterEmpty = guard.observeResponse(output.getText(), requestedToolCalls);

            if (requestedToolCalls == 0) {
                if (forceAfterEmpty) {
                    // Two consecutive empty responses — ask once, explicitly, for an answer.
                    return finalTurn(messages, EmptyResponseGuard.instruction(), toolCallHistory,
                            accumulatedUsage, guard.iterationsUsed(), loopStartNanos);
                }
                boolean empty = output.getText() == null || output.getText().isBlank();
                if (empty) {
                    // A single empty response is a provider hiccup, not an answer.
                    // Retry rather than returning "" to the caller; the guard escalates
                    // to a forced final turn if the next response is empty too.
                    log.debug("Empty assistant response on iteration {} — retrying", i + 1);
                    messages.add(output);
                    continue;
                }
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
                    hooks.fireVoid(ToolCallStartedEvent.of(
                            agentId, sessionKey, tc.name(), tc.arguments(), iteration));
                }

                String toolArguments = tc.arguments();

                // Repetition guard — identical consecutive calls mean a stuck model.
                RepetitionGuard.Verdict verdict = guard.observeToolCall(tc.name(), toolArguments);
                if (verdict == RepetitionGuard.Verdict.NOTIFY) {
                    String notice = RepetitionGuard.notice(tc.name());
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), notice));
                    toolCallHistory.add(ToolCallEvent.after(tc.name(), toolArguments, notice, iteration, sessionKey));
                    if (hooks != null) {
                        hooks.fireVoid(ToolCallEndedEvent.of(
                                agentId, sessionKey, tc.name(), toolArguments, notice, iteration));
                    }
                    continue;
                }

                // Approval floor — a per-tool minimum posture the model cannot talk past.
                ApprovalFloor floor = guard.floorFor(tc.name());
                if (floor == ApprovalFloor.DENY) {
                    String denial = "Tool call denied: `" + tc.name()
                            + "` is blocked by an operator approval floor and cannot be used.";
                    responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), denial));
                    toolCallHistory.add(ToolCallEvent.after(tc.name(), toolArguments, denial, iteration, sessionKey));
                    if (hooks != null) {
                        hooks.fireVoid(ToolCallEndedEvent.of(
                                agentId, sessionKey, tc.name(), toolArguments, denial, iteration));
                    }
                    continue;
                }

                // Optional approval gate. A PROMPT_ALWAYS floor forces the gate on for
                // this tool even when the session would otherwise skip approval.
                boolean approvalRequired = config.requireApproval() || floor == ApprovalFloor.PROMPT_ALWAYS;
                if (approvalRequired && approvalHandler != null) {
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
                                    hooks.fireVoid(ToolCallEndedEvent.of(
                                            agentId, sessionKey, tc.name(), toolArguments, denialResult, iteration));
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
                    // A tool that exists but was deferred and not yet discovered
                    // lands here. Name tool_search so the model can recover, rather
                    // than concluding the capability does not exist.
                    result = "ERROR: Unknown tool: " + tc.name()
                            + ". If you expected this tool to exist, call tool_search to look it up "
                            + "first — some tools are only listed on demand.";
                }

                // One-time budget checkpoint, appended to the next real tool result so
                // the model learns how much room it has left (Hermes pattern).
                String budgetWarning = guard.takeBudgetWarning();
                if (budgetWarning != null) {
                    result = result + "\n\n" + budgetWarning;
                }

                responses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));

                // Fire AFTER_TOOL_CALL hook
                var afterEvent = ToolCallEvent.after(tc.name(), toolArguments, result, iteration, sessionKey);
                toolCallHistory.add(afterEvent);
                if (hooks != null) {
                    hooks.fireVoid(ToolCallEndedEvent.of(
                            agentId, sessionKey, tc.name(), toolArguments, result, iteration));
                }
            }

            messages.add(ToolResponseMessage.builder().responses(responses).build());
        }

        log.warn("Explicit tool loop hit max iterations ({}) for session {}", config.maxIterations(), sessionKey);
        return finalTurn(messages, BudgetGuard.exhaustedInstruction(), toolCallHistory,
                accumulatedUsage, config.maxIterations(), loopStartNanos);
    }

    /**
     * Runs one last model call with <em>no tools attached</em> and an explicit
     * instruction to answer, then returns its text.
     *
     * <p>This is what a guard trip produces instead of the old bare
     * "Max iterations reached" string: the model still gets to summarise what it
     * accomplished. If that final call fails for any reason we fall back to the
     * instruction text rather than propagating — a guard must never turn a
     * partially successful run into an exception.
     */
    private LoopResult finalTurn(List<Message> messages, String instruction,
                                 List<ToolCallEvent> toolCallHistory,
                                 TokenUsage accumulatedUsage, int iterationsUsed,
                                 long loopStartNanos) {
        List<Message> finalMessages = new ArrayList<>(messages);
        finalMessages.add(new UserMessage(instruction));

        String text;
        try {
            ChatResponse response = chatModel.call(new Prompt(finalMessages));
            accumulatedUsage = accumulatedUsage.add(extractUsage(response));
            text = response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) text = instruction;
        } catch (Exception e) {
            log.warn("Final tool-less turn failed for session — returning guard instruction", e);
            text = instruction;
        }

        long durationMs = (System.nanoTime() - loopStartNanos) / 1_000_000;
        return new LoopResult(text, toolCallHistory, iterationsUsed, accumulatedUsage, durationMs);
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
