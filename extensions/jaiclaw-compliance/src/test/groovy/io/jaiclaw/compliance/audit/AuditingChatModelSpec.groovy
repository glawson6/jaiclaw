package io.jaiclaw.compliance.audit

import io.jaiclaw.audit.AuditEvent
import io.jaiclaw.audit.InMemoryAuditLogger
import io.jaiclaw.core.tenant.DefaultTenantContext
import io.jaiclaw.core.tenant.TenantContext
import io.jaiclaw.core.tenant.TenantContextHolder
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import reactor.core.publisher.Flux
import spock.lang.Specification

class AuditingChatModelSpec extends Specification {

    InMemoryAuditLogger logger = new InMemoryAuditLogger()

    private ChatModel delegate = new ChatModel() {
        @Override
        ChatResponse call(Prompt prompt) {
            return new ChatResponse([new Generation(new AssistantMessage("stub reply"))])
        }

        @Override
        Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(new ChatResponse([new Generation(new AssistantMessage("stub reply"))]))
        }
    }

    def "call emits a model.inference.request audit event with recipient"() {
        given:
        def wrapped = new AuditingChatModel(delegate, logger)
        def prompt = new Prompt([new UserMessage("hi")])

        when:
        wrapped.call(prompt)
        def events = logger.query(null, 10)

        then:
        events.size() == 1
        def e = events[0]
        e.action() == "model.inference.request"
        e.outcome() == AuditEvent.Outcome.SUCCESS
        e.recipients().size() == 1
    }

    def "data categories are derived from message roles in the prompt"() {
        given:
        def wrapped = new AuditingChatModel(delegate, logger)
        def prompt = new Prompt([
                new SystemMessage("you are a helper"),
                new UserMessage("tell me a joke"),
        ])

        when:
        wrapped.call(prompt)
        def events = logger.query(null, 10)

        then:
        events[0].dataCategories().containsAll(["user_utterance", "system_prompt"] as Set)
    }

    def "compliance metadata is sourced from the current TenantContext"() {
        given:
        def wrapped = new AuditingChatModel(delegate, logger)
        def ctx = new DefaultTenantContext("acme", "Acme", [
                (TenantContext.KEY_LAWFUL_BASIS): "contract",
                (TenantContext.KEY_RETENTION_DAYS): 2190,
                (TenantContext.KEY_CONSENT_TOKEN): "cnst_xyz",
        ])

        when:
        try {
            TenantContextHolder.set(ctx)
            wrapped.call(new Prompt([new UserMessage("hi")]))
        } finally {
            TenantContextHolder.clear()
        }
        def events = logger.query("acme", 10)

        then:
        events.size() == 1
        events[0].tenantId() == "acme"
        events[0].lawfulBasis() == "contract"
        events[0].retentionDays() == 2190
        events[0].consentToken() == "cnst_xyz"
    }

    def "delegate exception propagates but a FAILURE audit event is still emitted"() {
        given:
        def failing = new ChatModel() {
            @Override
            ChatResponse call(Prompt prompt) {
                throw new RuntimeException("provider oops")
            }
            @Override
            Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.error(new RuntimeException("stream oops"))
            }
        }
        def wrapped = new AuditingChatModel(failing, logger)

        when:
        wrapped.call(new Prompt([new UserMessage("hi")]))

        then:
        def caught = thrown(RuntimeException)
        caught.message == "provider oops"
        def events = logger.query(null, 10)
        events.size() == 1
        events[0].outcome() == AuditEvent.Outcome.FAILURE
    }

    def "audit failure does NOT break the request path"() {
        given:
        def brokenLogger = new io.jaiclaw.audit.AuditLogger() {
            void log(AuditEvent event) { throw new RuntimeException("audit is down") }
            List<AuditEvent> query(String tenantId, int limit) { return [] }
            Optional<AuditEvent> findById(String id) { return Optional.empty() }
            long count(String tenantId) { return 0 }
        }
        def wrapped = new AuditingChatModel(delegate, brokenLogger)

        when:
        def response = wrapped.call(new Prompt([new UserMessage("hi")]))

        then:
        noExceptionThrown()
        response != null
    }
}
