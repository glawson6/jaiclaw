package io.jaiclaw.compliance.audit;

import io.jaiclaw.audit.AuditEvent;
import io.jaiclaw.audit.AuditLogger;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.core.tenant.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * T1-3: transparent decorator over Spring AI's {@link ChatModel} that emits a
 * structured {@link AuditEvent} per LLM call. Fills the GDPR Art. 30 /
 * Art. 44 gap the previous audit trail had — every cross-border data
 * transfer to an LLM provider now has a matching {@code model.inference.request}
 * event with recipient identifier, data categories, and the caller's
 * compliance metadata (lawful basis, retention days, consent token) sourced
 * from the current {@link TenantContext}.
 *
 * <p>Wire semantics: constructed by {@link JaiClawComplianceAutoConfiguration}
 * only when {@code jaiclaw.compliance.effective.audit-chat-client=true}. The
 * decorator wraps every {@code ChatModel} bean discovered on the context via
 * a {@code BeanPostProcessor}. Consumers see no behavioural change beyond
 * the audit-event emission — return types, exception propagation, and
 * streaming semantics are identical.
 *
 * <p><strong>Recipient labels.</strong> The recipient string is
 * {@code <providerName>[-<region>]}. Provider name comes from the class name
 * of the delegate ChatModel (best-effort, since Spring AI doesn't expose it
 * declaratively). Region is unknown at this layer; deployers who want
 * region-tagged events can wrap this decorator again with their own
 * region-aware layer.
 */
public class AuditingChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(AuditingChatModel.class);

    private final ChatModel delegate;
    private final AuditLogger auditLogger;
    private final String recipient;

    public AuditingChatModel(ChatModel delegate, AuditLogger auditLogger) {
        this.delegate = delegate;
        this.auditLogger = auditLogger;
        this.recipient = deriveRecipient(delegate);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        long start = System.nanoTime();
        try {
            ChatResponse response = delegate.call(prompt);
            emit(prompt, AuditEvent.Outcome.SUCCESS, start, null);
            return response;
        } catch (RuntimeException e) {
            emit(prompt, AuditEvent.Outcome.FAILURE, start, e.getMessage());
            throw e;
        }
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        long start = System.nanoTime();
        return delegate.stream(prompt)
                .doOnComplete(() -> emit(prompt, AuditEvent.Outcome.SUCCESS, start, null))
                .doOnError(e -> emit(prompt, AuditEvent.Outcome.FAILURE, start, e.getMessage()));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    private void emit(Prompt prompt, AuditEvent.Outcome outcome, long startNanos, String errorMsg) {
        try {
            TenantContext ctx = TenantContextHolder.get();
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            AuditEvent event = AuditEvent.builder()
                    .id("llm-" + UUID.randomUUID())
                    .timestamp(Instant.now())
                    .tenantId(ctx != null ? ctx.getTenantId() : null)
                    .actor(ctx != null ? ctx.getTenantId() : "system")
                    .action("model.inference.request")
                    .resource(recipient)
                    .outcome(outcome)
                    .details(java.util.Map.of(
                            "durationMs", durationMs,
                            "messageCount", prompt != null && prompt.getInstructions() != null
                                    ? prompt.getInstructions().size() : 0,
                            "errorMessage", errorMsg != null ? errorMsg : ""))
                    // Compliance fields sourced from the current tenant's context
                    .lawfulBasis(ctx != null ? ctx.getLawfulBasis() : null)
                    .dataCategories(deriveDataCategories(prompt))
                    .recipients(Set.of(recipient))
                    .retentionDays(ctx != null ? ctx.getRetentionDays() : null)
                    .consentToken(ctx != null ? ctx.getConsentToken() : null)
                    .build();
            auditLogger.log(event);
        } catch (RuntimeException e) {
            // Audit failure must never break the request path.
            log.debug("Failed to emit LLM audit event: {}", e.getMessage());
        }
    }

    private static Set<String> deriveDataCategories(Prompt prompt) {
        if (prompt == null || prompt.getInstructions() == null || prompt.getInstructions().isEmpty()) {
            return Set.of();
        }
        Set<String> cats = new LinkedHashSet<>();
        List<Message> messages = prompt.getInstructions();
        for (Message m : messages) {
            String role = m.getMessageType() != null ? m.getMessageType().getValue() : "unknown";
            switch (role) {
                case "user" -> cats.add("user_utterance");
                case "system" -> cats.add("system_prompt");
                case "assistant" -> cats.add("assistant_reply");
                case "tool" -> cats.add("tool_result");
                default -> cats.add("message:" + role);
            }
        }
        return cats;
    }

    private static String deriveRecipient(ChatModel delegate) {
        if (delegate == null) return "unknown";
        String cls = delegate.getClass().getSimpleName();
        // Strip common Spring AI suffixes to leave the provider name.
        return cls.replaceAll("(?i)ChatModel$", "")
                .replaceAll("(?i)ChatOptions$", "")
                .toLowerCase();
    }

    /** Package-private accessor for tests. */
    ChatModel delegate() {
        return delegate;
    }
}
