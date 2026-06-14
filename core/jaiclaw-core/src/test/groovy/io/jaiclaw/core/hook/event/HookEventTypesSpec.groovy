package io.jaiclaw.core.hook.event

import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.TokenUsage
import spock.lang.Specification

import java.time.Instant

/**
 * Locks the Phase 3 P3.1 sealed {@link HookEvent} hierarchy:
 * every subtype carries the contract fields (agentId, sessionKey, timestamp)
 * and the convenience {@code of(...)} factories return an instance with
 * the right runtime class.
 */
class HookEventTypesSpec extends Specification {

    def "HookEvent is sealed with all 20 expected permits"() {
        when:
        Set<String> permitted = HookEvent.class.getPermittedSubclasses()
                .collect { it.simpleName } as Set

        then:
        permitted == [
                "AgentStartedEvent", "AgentEndedEvent",
                "BeforeModelResolveEvent", "BeforePromptBuildEvent",
                "LlmInputEvent", "LlmOutputEvent",
                "ToolCallStartedEvent", "ToolCallEndedEvent",
                "MessageReceivedEvent", "MessageSendingEvent", "MessageSentEvent",
                "SessionStartedEvent", "SessionEndedEvent", "BeforeResetEvent",
                "BeforeCompactionEvent", "AfterCompactionEvent",
                "TaskStateChangedEvent",
                // Phase 4 task 4.7 — AgentMind first-class events
                "SoulUpdatedEvent", "MemoryUpdatedEvent", "TendenciesUpdatedEvent",
        ] as Set
    }

    def "AgentStartedEvent.of populates contract fields"() {
        when:
        AgentStartedEvent event = AgentStartedEvent.of("a1", "sess", "hello")

        then:
        event.agentId() == "a1"
        event.sessionKey() == "sess"
        event.userInput() == "hello"
        event.timestamp() != null
        event instanceof HookEvent
    }

    def "AgentEndedEvent.of carries the assistant message"() {
        given:
        AssistantMessage msg = AssistantMessage.builder()
                .id("m1")
                .content("done")
                .usage(new TokenUsage(0, 0, 0, 0))
                .build()

        when:
        AgentEndedEvent event = AgentEndedEvent.of("a1", "sess", msg)

        then:
        event.assistantMessage().is(msg)
    }

    def "BeforePromptBuildEvent.withSystemPrompt produces a copy"() {
        given:
        BeforePromptBuildEvent original = BeforePromptBuildEvent.of("a1", "sess", "first")

        when:
        BeforePromptBuildEvent updated = original.withSystemPrompt("second")

        then:
        original.systemPrompt() == "first"
        updated.systemPrompt() == "second"
        updated.agentId() == original.agentId()
        updated.sessionKey() == original.sessionKey()
        updated.timestamp() == original.timestamp()
    }

    def "ToolCallEndedEvent computes success from result prefix"() {
        expect:
        ToolCallEndedEvent.of("a1", "sess", "tool", "{}", "ok", 1).success()
        !ToolCallEndedEvent.of("a1", "sess", "tool", "{}", "ERROR: boom", 1).success()
        !ToolCallEndedEvent.of("a1", "sess", "tool", "{}", "Tool call denied: nope", 1).success()
    }

    def "every event subtype carries contract fields"() {
        when:
        HookEvent event = factory.call()

        then:
        event.agentId() != null
        event.timestamp() != null
        event instanceof HookEvent

        where:
        factory << [
                { -> AgentStartedEvent.of("a", "s", "u") },
                { -> AgentEndedEvent.of("a", "s", null) },
                { -> BeforeModelResolveEvent.of("a", "s") },
                { -> BeforePromptBuildEvent.of("a", "s", "p") },
                { -> LlmInputEvent.of("a", "s", "u") },
                { -> LlmOutputEvent.of("a", "s", "r") },
                { -> ToolCallStartedEvent.of("a", "s", "t", "{}", 1) },
                { -> ToolCallEndedEvent.of("a", "s", "t", "{}", "ok", 1) },
                { -> MessageReceivedEvent.of("a", "s", "ch", "ac", "p", "c") },
                { -> MessageSendingEvent.of("a", "s", "ch", "ac", "p", "c") },
                { -> MessageSentEvent.of("a", "s", "ch", "ac", "p", "c", true) },
                { -> SessionStartedEvent.of("a", "s") },
                { -> SessionEndedEvent.of("a", "s", "expired") },
                { -> BeforeResetEvent.of("a", "s") },
                { -> BeforeCompactionEvent.of("a", "s", 1000, 500) },
                { -> AfterCompactionEvent.of("a", "s", 1000, 400) },
        ]
    }

    def "SoulUpdatedEvent.ofAgent populates contract fields"() {
        when:
        SoulUpdatedEvent event = SoulUpdatedEvent.ofAgent("tenant-a", "bot", 3L, "operator")

        then:
        event.agentId() == "bot"
        event.sessionKey() == null
        event.scope() == io.jaiclaw.core.model.SoulScope.AGENT
        event.tenantId() == "tenant-a"
        event.version() == 3L
        event.actor() == "operator"
        event.timestamp() != null
        event instanceof HookEvent
    }

    def "SoulUpdatedEvent.ofTenant uses agentId='*'"() {
        when:
        SoulUpdatedEvent event = SoulUpdatedEvent.ofTenant("tenant-a", 1L, "operator")

        then:
        event.agentId() == "*"
        event.scope() == io.jaiclaw.core.model.SoulScope.TENANT
    }

    def "MemoryUpdatedEvent.ofPeer carries the session + canonical user"() {
        when:
        MemoryUpdatedEvent event = MemoryUpdatedEvent.ofPeer(
                "tenant-a", "bot", "user-1", "bot:slack:acct:u", 5L, "agent")

        then:
        event.agentId() == "bot"
        event.sessionKey() == "bot:slack:acct:u"
        event.scope() == "PEER"
        event.tenantId() == "tenant-a"
        event.canonicalUserId() == "user-1"
        event.version() == 5L
        event.actor() == "agent"
        event instanceof HookEvent
    }

    def "TendenciesUpdatedEvent.of populates provider + version"() {
        when:
        TendenciesUpdatedEvent event = TendenciesUpdatedEvent.of(
                "tenant-a", "user-1", "bot", "bot:slack:acct:u", 2L, "deterministic")

        then:
        event.agentId() == "bot"
        event.sessionKey() == "bot:slack:acct:u"
        event.tenantId() == "tenant-a"
        event.canonicalUserId() == "user-1"
        event.version() == 2L
        event.provider() == "deterministic"
        event instanceof HookEvent
    }
}
