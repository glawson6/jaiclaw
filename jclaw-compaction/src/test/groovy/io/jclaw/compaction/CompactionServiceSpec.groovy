package io.jclaw.compaction

import io.jclaw.core.model.*
import spock.lang.Specification

import java.util.function.Function

class CompactionServiceSpec extends Specification {

    def "does not compact when disabled"() {
        given:
        def service = new CompactionService(CompactionConfig.DISABLED)
        def messages = buildMessages(10)

        when:
        def result = service.compactIfNeeded(messages, 1000, { it } as Function)

        then:
        result == null
    }

    def "does not compact when under threshold"() {
        given:
        def service = new CompactionService(CompactionConfig.DEFAULT)
        def messages = [new UserMessage("1", "hello", "user")]

        when:
        def result = service.compactIfNeeded(messages, 100_000, { it } as Function)

        then:
        result == null
    }

    def "triggers compaction when over threshold"() {
        given:
        def config = new CompactionConfig(true, 0.5, 20, null)
        def service = new CompactionService(config)
        // Create enough messages to exceed 50% of a small context window
        def messages = buildMessages(50)
        Function<String, String> llm = { "Summary of conversation." }

        when:
        def result = service.compactIfNeeded(messages, 200, llm)

        then:
        result != null
        result.messagesRemoved() > 0
        result.summary().contains("Summary")
    }

    def "applyCompaction returns compacted message list"() {
        given:
        def config = new CompactionConfig(true, 0.5, 20, null)
        def service = new CompactionService(config)
        def messages = buildMessages(50)
        Function<String, String> llm = { "Summary of conversation." }

        when:
        def compacted = service.applyCompaction(messages, 200, llm)

        then:
        compacted.size() < messages.size()
        compacted[0] instanceof SystemMessage
        compacted[0].content().contains("[Context Summary]")
    }

    def "applyCompaction returns original messages when compaction not needed"() {
        given:
        def service = new CompactionService(CompactionConfig.DEFAULT)
        def messages = [new UserMessage("1", "hi", "user")]

        when:
        def result = service.applyCompaction(messages, 100_000, { it } as Function)

        then:
        result == messages
    }

    private List<Message> buildMessages(int count) {
        List<Message> messages = []
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                messages.add(new UserMessage("u${i}", "User message number ${i} with some content", "user1"))
            } else {
                messages.add(new AssistantMessage("a${i}", "Assistant response number ${i} with details", "model1"))
            }
        }
        return messages
    }
}
