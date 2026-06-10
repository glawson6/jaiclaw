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

    def "HookEvent is sealed with all 16 expected permits"() {
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
}
