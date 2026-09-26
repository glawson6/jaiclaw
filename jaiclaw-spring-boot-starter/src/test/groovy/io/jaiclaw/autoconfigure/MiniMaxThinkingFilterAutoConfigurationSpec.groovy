package io.jaiclaw.autoconfigure

import io.jaiclaw.autoconfigure.MiniMaxThinkingFilterAutoConfiguration.ThinkingFilterChatModel
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.ai.tool.ToolCallback
import reactor.core.publisher.Flux
import spock.lang.Specification
import spock.lang.Subject

class MiniMaxThinkingFilterAutoConfigurationSpec extends Specification {

    private static AssistantMessage textMessage(String content) {
        return new AssistantMessage(content)
    }

    private static AssistantMessage thinkingMessage(String content, String signature) {
        return AssistantMessage.builder()
                .content(content)
                .properties(Map.of("signature", signature))
                .build()
    }

    // --- ThinkingFilterChatModel tests ---

    def "filter removes thinking generations and keeps text generations"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)

        def thinkingGen = new Generation(thinkingMessage("thinking content", "abc123"))
        def textGen = new Generation(textMessage("Hello, world!"))
        def response = new ChatResponse([thinkingGen, textGen])

        delegate.call(_) >> response

        when:
        def result = filter.call(new Prompt("test"))

        then:
        result.results.size() == 1
        result.results[0].output.text == "Hello, world!"
    }

    def "filter passes through single-generation response unchanged"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)

        def singleGen = new Generation(textMessage("single response"))
        def response = new ChatResponse([singleGen])

        delegate.call(_) >> response

        when:
        def result = filter.call(new Prompt("test"))

        then:
        result.results.size() == 1
        result.results[0].output.text == "single response"
    }

    def "filter passes through null response unchanged"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)

        delegate.call(_) >> null

        when:
        def result = filter.call(new Prompt("test"))

        then:
        result == null
    }

    def "filter fallback keeps last generation when all are thinking"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)

        def thinking1 = new Generation(thinkingMessage("thinking 1", "sig1"))
        def thinking2 = new Generation(thinkingMessage("thinking 2", "sig2"))
        def response = new ChatResponse([thinking1, thinking2])

        delegate.call(_) >> response

        when:
        def result = filter.call(new Prompt("test"))

        then:
        result.results.size() == 1
        result.results[0].output.text == "thinking 2"
    }

    def "filter keeps multiple text generations when mixed with thinking"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)

        def thinkingGen = new Generation(thinkingMessage("thinking", "sig"))
        def text1 = new Generation(textMessage("text 1"))
        def text2 = new Generation(textMessage("text 2"))
        def response = new ChatResponse([thinkingGen, text1, text2])

        delegate.call(_) >> response

        when:
        def result = filter.call(new Prompt("test"))

        then:
        result.results.size() == 2
        result.results[0].output.text == "text 1"
        result.results[1].output.text == "text 2"
    }

    def "filter preserves response metadata"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)

        def thinkingGen = new Generation(thinkingMessage("thinking", "sig"))
        def textGen = new Generation(textMessage("text"))
        def response = new ChatResponse([thinkingGen, textGen])

        delegate.call(_) >> response

        when:
        def result = filter.call(new Prompt("test"))

        then:
        result.metadata == response.metadata
    }

    // --- BeanPostProcessor tests ---

    def "BeanPostProcessor wraps a ChatModel bean"() {
        given:
        def processor = MiniMaxThinkingFilterAutoConfiguration.miniMaxThinkingFilter()
        def chatModel = Mock(ChatModel)

        when:
        def result = processor.postProcessAfterInitialization(chatModel, "testChatModel")

        then:
        result instanceof ThinkingFilterChatModel
        (result as ThinkingFilterChatModel).delegate.is(chatModel)
    }

    def "BeanPostProcessor skips beans that are already ThinkingFilterChatModel"() {
        given:
        def processor = MiniMaxThinkingFilterAutoConfiguration.miniMaxThinkingFilter()
        def alreadyWrapped = new ThinkingFilterChatModel(Mock(ChatModel))

        when:
        def result = processor.postProcessAfterInitialization(alreadyWrapped, "testChatModel")

        then:
        result.is(alreadyWrapped)
    }

    def "BeanPostProcessor ignores non-ChatModel beans"() {
        given:
        def processor = MiniMaxThinkingFilterAutoConfiguration.miniMaxThinkingFilter()
        def notAChatModel = "just a string"

        when:
        def result = processor.postProcessAfterInitialization(notAChatModel, "someBean")

        then:
        result.is(notAChatModel)
    }

    // --- Regression: the decorator must delegate every ChatModel method it does
    // not deliberately alter. Overriding only call(Prompt) left getDefaultOptions()
    // returning empty generic options (stripping tools + model from every
    // ChatClient request) and stream() throwing UnsupportedOperationException.

    def "filter delegates getDefaultOptions instead of returning empty generic options"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)

        // A provider's real options: a concrete tool-capable type carrying a model
        // name and the tool callbacks. This is what must survive the wrapping.
        def toolCallback = Mock(ToolCallback)
        def realOptions = ToolCallingChatOptions.builder()
                .model("claude-sonnet-4-5")
                .toolCallbacks([toolCallback])
                .build()

        when:
        def result = filter.getDefaultOptions()

        then:
        1 * delegate.getDefaultOptions() >> realOptions
        result.is(realOptions)
        result.model == "claude-sonnet-4-5"
        result.toolCallbacks.size() == 1
    }

    def "filter delegates getOptions"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)
        def realOptions = ToolCallingChatOptions.builder().model("gpt-5").build()

        when:
        def result = filter.getOptions()

        then:
        1 * delegate.getOptions() >> realOptions
        result.model == "gpt-5"
    }

    def "filter supports streaming rather than throwing UnsupportedOperationException"() {
        given:
        def delegate = Mock(ChatModel)
        @Subject def filter = new ThinkingFilterChatModel(delegate)

        def thinkingGen = new Generation(thinkingMessage("reasoning", "sig-1"))
        def textGen = new Generation(textMessage("streamed answer"))
        delegate.stream(_) >> Flux.just(new ChatResponse([thinkingGen, textGen]))

        when:
        def emitted = filter.stream(new Prompt("test")).collectList().block()

        then:
        noExceptionThrown()
        emitted.size() == 1

        and: "thinking blocks are filtered on the streaming path too"
        emitted[0].results.size() == 1
        emitted[0].results[0].output.text == "streamed answer"
    }
}
