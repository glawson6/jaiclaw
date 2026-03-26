package io.jaiclaw.compaction

import io.jaiclaw.core.model.*
import spock.lang.Specification

import java.util.function.Function

class CompactionServiceAdapterSpec extends Specification {

    def "delegates to CompactionService.applyCompaction"() {
        given:
        def config = new CompactionConfig(true, 0.5, 20, null)
        def service = new CompactionService(config)
        def adapter = new CompactionServiceAdapter(service)
        def messages = buildMessages(50)
        Function<String, String> llm = { "Summary." }

        when:
        def result = adapter.compactIfNeeded(messages, 200, llm)

        then:
        result.size() < messages.size()
        result[0] instanceof SystemMessage
        result[0].content().contains("[Context Summary]")
    }

    def "returns original messages when compaction not needed"() {
        given:
        def service = new CompactionService(CompactionConfig.DEFAULT)
        def adapter = new CompactionServiceAdapter(service)
        def messages = [new UserMessage("1", "hi", "user")]

        when:
        def result = adapter.compactIfNeeded(messages, 100_000, { it } as Function)

        then:
        result == messages
    }

    private List<Message> buildMessages(int count) {
        (0..<count).collect { i ->
            i % 2 == 0
                    ? new UserMessage("u${i}", "User message ${i} content", "user1")
                    : new AssistantMessage("a${i}", "Assistant response ${i}", "model1")
        }
    }
}
