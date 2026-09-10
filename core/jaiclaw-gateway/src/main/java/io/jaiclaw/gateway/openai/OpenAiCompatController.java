package io.jaiclaw.gateway.openai;

import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.tenant.TenantContextHolder;
import io.jaiclaw.gateway.GatewayProperties;
import io.jaiclaw.gateway.GatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * OpenAI-compatible chat endpoint at {@code POST /v1/chat/completions}, so tools
 * and SDKs that speak the OpenAI wire format can talk to a JaiClaw agent
 * unchanged.
 *
 * <p><strong>Off by default</strong> ({@code jaiclaw.gateway.openai-api-enabled}).
 * It looks to a client like an unauthenticated model API, so an adopter must turn
 * it on deliberately and front it with the same authentication as the rest of
 * their surface — this controller performs none of its own.
 *
 * <p>The {@code model} field selects a JaiClaw <em>agent id</em>, not a provider
 * model. Sampling parameters are accepted and ignored: the deployment owns model
 * configuration, and honouring them would let any caller reconfigure the agent.
 *
 * <p>Phase 5 of the 1.2.0 plan.
 */
@RestController
public class OpenAiCompatController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatController.class);

    /** Long enough for a tool-using agent turn; the client can always reconnect. */
    private static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;

    private final GatewayService gatewayService;
    private final GatewayProperties properties;

    public OpenAiCompatController(GatewayService gatewayService, GatewayProperties properties) {
        this.gatewayService = gatewayService;
        this.properties = properties;
    }

    @PostMapping(path = "/v1/chat/completions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Object chatCompletions(@RequestBody ChatCompletionRequest request,
                                  @RequestHeader Map<String, String> headers) {
        if (!properties.openaiApiEnabled()) {
            // 404 rather than 403: a disabled surface should not advertise that it exists.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(error("The OpenAI-compatible API is not enabled on this deployment."));
        }
        String prompt = request.latestUserContent();
        if (prompt == null || prompt.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(error("messages must contain at least one user message with content."));
        }

        String model = request.model() == null || request.model().isBlank() ? "default" : request.model();
        String id = "chatcmpl-" + UUID.randomUUID();
        String sessionKey = sessionKeyFor(model, headers);

        gatewayService.resolveTenant(headers).ifPresent(TenantContextHolder::set);
        try {
            return request.streaming()
                    ? stream(id, model, sessionKey, prompt)
                    : ResponseEntity.ok(complete(id, model, sessionKey, prompt));
        } finally {
            TenantContextHolder.clear();
        }
    }

    private ChatCompletionResponse complete(String id, String model, String sessionKey, String prompt) {
        AssistantMessage reply = gatewayService.handleAsync(sessionKey, prompt).join();
        String content = reply == null || reply.content() == null ? "" : reply.content();
        int promptTokens = reply != null && reply.usage() != null ? reply.usage().inputTokens() : 0;
        int completionTokens = reply != null && reply.usage() != null ? reply.usage().outputTokens() : 0;
        return ChatCompletionResponse.of(id, model, content, promptTokens, completionTokens);
    }

    /**
     * Streams the reply as {@code chat.completion.chunk} frames terminated by
     * {@code [DONE]}.
     *
     * <p>The underlying runtime returns a whole message rather than a token
     * stream on this path, so the reply is delivered as one content frame between
     * the role and stop frames. That is a valid SSE completion for every client
     * that speaks this format — they see a well-formed stream, just not
     * token-by-token.
     */
    private SseEmitter stream(String id, String model, String sessionKey, String prompt) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Thread.ofVirtual().name("openai-sse-" + id).start(() -> {
            try {
                emitter.send(SseEmitter.event().data(ChatCompletionChunk.start(id, model)));
                AssistantMessage reply = gatewayService.handleAsync(sessionKey, prompt).join();
                String content = reply == null || reply.content() == null ? "" : reply.content();
                if (!content.isEmpty()) {
                    emitter.send(SseEmitter.event().data(ChatCompletionChunk.content(id, model, content)));
                }
                emitter.send(SseEmitter.event().data(ChatCompletionChunk.stop(id, model)));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (IOException | RuntimeException e) {
                log.warn("OpenAI-compatible stream {} failed", id, e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * Per-request sessions are stateless — the client's message list is the whole
     * history, which is what most OpenAI clients assume. {@code by-user-header}
     * gives a durable session per {@code X-JaiClaw-User}, so JaiClaw's own memory
     * and session features apply.
     */
    private String sessionKeyFor(String model, Map<String, String> headers) {
        if (properties.openaiSessionByUserHeader()) {
            String user = headerIgnoreCase(headers, "x-jaiclaw-user");
            if (user != null && !user.isBlank()) {
                return model + ":openai:api:" + user.trim();
            }
        }
        return model + ":openai:api:" + UUID.randomUUID();
    }

    private static String headerIgnoreCase(Map<String, String> headers, String name) {
        if (headers == null) return null;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    /** OpenAI-shaped error body, so clients parse failures the way they expect. */
    private static Map<String, Object> error(String message) {
        return Map.of("error", Map.of(
                "message", message,
                "type", "invalid_request_error",
                "param", (Object) "",
                "code", (Object) ""));
    }
}
