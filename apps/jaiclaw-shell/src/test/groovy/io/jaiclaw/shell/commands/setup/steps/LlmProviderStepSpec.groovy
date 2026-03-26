package io.jaiclaw.shell.commands.setup.steps

import io.jaiclaw.shell.commands.setup.OnboardResult
import io.jaiclaw.shell.commands.setup.validation.LlmConnectivityTester
import org.springframework.shell.component.flow.ComponentFlow
import org.springframework.shell.component.flow.SingleItemSelectorSpec
import org.springframework.shell.component.context.BaseComponentContext
import spock.lang.Specification

class LlmProviderStepSpec extends Specification {

    def "name returns LLM Provider"() {
        given:
        def step = new LlmProviderStep(Mock(ComponentFlow.Builder), Mock(LlmConnectivityTester))

        expect:
        step.name() == "LLM Provider"
    }

    def "returns false when provider selection is null"() {
        given:
        def flowBuilder = Mock(ComponentFlow.Builder)
        def selectorSpec = Mock(SingleItemSelectorSpec)
        def flow = Mock(ComponentFlow)
        def flowResult = Mock(ComponentFlow.ComponentFlowResult)
        def context = new BaseComponentContext()

        flowBuilder.clone() >> flowBuilder
        flowBuilder.reset() >> flowBuilder
        flowBuilder.withSingleItemSelector(_) >> selectorSpec
        selectorSpec.name(_) >> selectorSpec
        selectorSpec.selectItem(_, _) >> selectorSpec
        selectorSpec.and() >> flowBuilder
        flowBuilder.build() >> flow
        flow.run() >> flowResult
        flowResult.getContext() >> context

        def step = new LlmProviderStep(flowBuilder, Mock(LlmConnectivityTester))
        def result = new OnboardResult()

        when:
        def ok = step.execute(result)

        then:
        !ok
    }

    def "completes full flow for anthropic — key validated then model selected"() {
        given:
        def flowBuilder = Mock(ComponentFlow.Builder)
        def llmTester = Mock(LlmConnectivityTester)
        def step = new LlmProviderStep(flowBuilder, llmTester)
        def result = new OnboardResult()

        def providerContext = new BaseComponentContext()
        providerContext.put("provider", "anthropic")

        def modelContext = new BaseComponentContext()
        modelContext.put("model", "claude-sonnet-4-6")

        def selectorSpec = Mock(SingleItemSelectorSpec)

        flowBuilder.clone() >> flowBuilder
        flowBuilder.reset() >> flowBuilder
        flowBuilder.withSingleItemSelector(_) >> selectorSpec
        selectorSpec.name(_) >> selectorSpec
        selectorSpec.selectItem(_, _) >> selectorSpec
        selectorSpec.and() >> flowBuilder

        def providerFlow = Mock(ComponentFlow)
        def modelFlow = Mock(ComponentFlow)
        def providerFlowResult = Mock(ComponentFlow.ComponentFlowResult)
        def modelFlowResult = Mock(ComponentFlow.ComponentFlowResult)

        flowBuilder.build() >>> [providerFlow, modelFlow]
        providerFlow.run() >> providerFlowResult
        providerFlowResult.getContext() >> providerContext
        modelFlow.run() >> modelFlowResult
        modelFlowResult.getContext() >> modelContext

        // Simulate stdin with API key
        def originalIn = System.in
        System.in = new ByteArrayInputStream("sk-ant-test-key-123\n".bytes)

        // Validation call uses first model in list (claude-sonnet-4-6)
        llmTester.test("anthropic", "sk-ant-test-key-123", "claude-sonnet-4-6", null) >>
                new LlmConnectivityTester.TestResult(true, "OK")

        when:
        def ok = step.execute(result)

        then:
        ok
        result.llmProvider() == "anthropic"
        result.llmApiKey() == "sk-ant-test-key-123"
        result.llmModel() == "claude-sonnet-4-6"

        cleanup:
        System.in = originalIn
    }

    def "validates API key before model selection — failed validation continues"() {
        given:
        def flowBuilder = Mock(ComponentFlow.Builder)
        def llmTester = Mock(LlmConnectivityTester)
        def step = new LlmProviderStep(flowBuilder, llmTester)
        def result = new OnboardResult()

        def providerContext = new BaseComponentContext()
        providerContext.put("provider", "openai")

        def modelContext = new BaseComponentContext()
        modelContext.put("model", "gpt-4o")

        def selectorSpec = Mock(SingleItemSelectorSpec)

        flowBuilder.clone() >> flowBuilder
        flowBuilder.reset() >> flowBuilder
        flowBuilder.withSingleItemSelector(_) >> selectorSpec
        selectorSpec.name(_) >> selectorSpec
        selectorSpec.selectItem(_, _) >> selectorSpec
        selectorSpec.and() >> flowBuilder

        def providerFlow = Mock(ComponentFlow)
        def modelFlow = Mock(ComponentFlow)
        def providerFlowResult = Mock(ComponentFlow.ComponentFlowResult)
        def modelFlowResult = Mock(ComponentFlow.ComponentFlowResult)

        flowBuilder.build() >>> [providerFlow, modelFlow]
        providerFlow.run() >> providerFlowResult
        providerFlowResult.getContext() >> providerContext
        modelFlow.run() >> modelFlowResult
        modelFlowResult.getContext() >> modelContext

        def originalIn = System.in
        System.in = new ByteArrayInputStream("sk-bad-key\n".bytes)

        // Validation fails but wizard continues
        llmTester.test("openai", "sk-bad-key", "gpt-4o", null) >>
                new LlmConnectivityTester.TestResult(false, "Invalid API key")

        when:
        def ok = step.execute(result)

        then:
        ok
        result.llmProvider() == "openai"
        result.llmApiKey() == "sk-bad-key"
        result.llmModel() == "gpt-4o"

        cleanup:
        System.in = originalIn
    }

    def "returns false when API key is blank"() {
        given:
        def flowBuilder = Mock(ComponentFlow.Builder)
        def llmTester = Mock(LlmConnectivityTester)
        def step = new LlmProviderStep(flowBuilder, llmTester)
        def result = new OnboardResult()

        def providerContext = new BaseComponentContext()
        providerContext.put("provider", "anthropic")

        def selectorSpec = Mock(SingleItemSelectorSpec)

        flowBuilder.clone() >> flowBuilder
        flowBuilder.reset() >> flowBuilder
        flowBuilder.withSingleItemSelector(_) >> selectorSpec
        selectorSpec.name(_) >> selectorSpec
        selectorSpec.selectItem(_, _) >> selectorSpec
        selectorSpec.and() >> flowBuilder

        def providerFlow = Mock(ComponentFlow)
        def providerFlowResult = Mock(ComponentFlow.ComponentFlowResult)

        flowBuilder.build() >> providerFlow
        providerFlow.run() >> providerFlowResult
        providerFlowResult.getContext() >> providerContext

        def originalIn = System.in
        System.in = new ByteArrayInputStream("\n".bytes)

        when:
        def ok = step.execute(result)

        then:
        !ok
        result.llmApiKey() == null

        cleanup:
        System.in = originalIn
    }

    def "ollama flow reads URL with default then selects model"() {
        given:
        def flowBuilder = Mock(ComponentFlow.Builder)
        def llmTester = Mock(LlmConnectivityTester)
        def step = new LlmProviderStep(flowBuilder, llmTester)
        def result = new OnboardResult()

        def providerContext = new BaseComponentContext()
        providerContext.put("provider", "ollama")

        def modelContext = new BaseComponentContext()
        modelContext.put("model", "llama3")

        def selectorSpec = Mock(SingleItemSelectorSpec)

        flowBuilder.clone() >> flowBuilder
        flowBuilder.reset() >> flowBuilder
        flowBuilder.withSingleItemSelector(_) >> selectorSpec
        selectorSpec.name(_) >> selectorSpec
        selectorSpec.selectItem(_, _) >> selectorSpec
        selectorSpec.and() >> flowBuilder

        def providerFlow = Mock(ComponentFlow)
        def modelFlow = Mock(ComponentFlow)
        def providerFlowResult = Mock(ComponentFlow.ComponentFlowResult)
        def modelFlowResult = Mock(ComponentFlow.ComponentFlowResult)

        flowBuilder.build() >>> [providerFlow, modelFlow]
        providerFlow.run() >> providerFlowResult
        providerFlowResult.getContext() >> providerContext
        modelFlow.run() >> modelFlowResult
        modelFlowResult.getContext() >> modelContext

        // Empty line → uses default URL
        def originalIn = System.in
        System.in = new ByteArrayInputStream("\n".bytes)

        when:
        def ok = step.execute(result)

        then:
        ok
        result.llmProvider() == "ollama"
        result.ollamaBaseUrl() == "http://localhost:11434"
        result.llmModel() == "llama3"

        cleanup:
        System.in = originalIn
    }

    def "returns false when model selection is null"() {
        given:
        def flowBuilder = Mock(ComponentFlow.Builder)
        def llmTester = Mock(LlmConnectivityTester)
        def step = new LlmProviderStep(flowBuilder, llmTester)
        def result = new OnboardResult()

        def providerContext = new BaseComponentContext()
        providerContext.put("provider", "ollama")

        def modelContext = new BaseComponentContext()
        // no "model" key

        def selectorSpec = Mock(SingleItemSelectorSpec)

        flowBuilder.clone() >> flowBuilder
        flowBuilder.reset() >> flowBuilder
        flowBuilder.withSingleItemSelector(_) >> selectorSpec
        selectorSpec.name(_) >> selectorSpec
        selectorSpec.selectItem(_, _) >> selectorSpec
        selectorSpec.and() >> flowBuilder

        def providerFlow = Mock(ComponentFlow)
        def modelFlow = Mock(ComponentFlow)
        def providerFlowResult = Mock(ComponentFlow.ComponentFlowResult)
        def modelFlowResult = Mock(ComponentFlow.ComponentFlowResult)

        flowBuilder.build() >>> [providerFlow, modelFlow]
        providerFlow.run() >> providerFlowResult
        providerFlowResult.getContext() >> providerContext
        modelFlow.run() >> modelFlowResult
        modelFlowResult.getContext() >> modelContext

        def originalIn = System.in
        System.in = new ByteArrayInputStream("\n".bytes)

        when:
        def ok = step.execute(result)

        then:
        !ok
        result.llmProvider() == "ollama"
        result.llmModel() == null

        cleanup:
        System.in = originalIn
    }

    def "model selector chains selectItem return values for all provider models"() {
        given:
        def flowBuilder = Mock(ComponentFlow.Builder)
        def llmTester = Mock(LlmConnectivityTester)
        def step = new LlmProviderStep(flowBuilder, llmTester)
        def result = new OnboardResult()

        def providerContext = new BaseComponentContext()
        providerContext.put("provider", "openai")

        def modelContext = new BaseComponentContext()
        modelContext.put("model", "gpt-4o")

        def selectorSpec = Mock(SingleItemSelectorSpec)

        flowBuilder.clone() >> flowBuilder
        flowBuilder.reset() >> flowBuilder
        flowBuilder.withSingleItemSelector(_) >> selectorSpec
        selectorSpec.name(_) >> selectorSpec
        selectorSpec.selectItem(_, _) >> selectorSpec
        selectorSpec.and() >> flowBuilder

        def flow = Mock(ComponentFlow)
        def providerResult = Mock(ComponentFlow.ComponentFlowResult)
        def modelResult = Mock(ComponentFlow.ComponentFlowResult)

        flowBuilder.build() >>> [flow, flow]
        flow.run() >>> [providerResult, modelResult]
        providerResult.getContext() >> providerContext
        modelResult.getContext() >> modelContext

        def originalIn = System.in
        System.in = new ByteArrayInputStream("sk-test\n".bytes)

        llmTester.test("openai", "sk-test", "gpt-4o", null) >>
                new LlmConnectivityTester.TestResult(true, "OK")

        when:
        def ok = step.execute(result)

        then:
        ok
        result.llmModel() == "gpt-4o"

        and: "selectItem called for 3 provider items + 5 openai models"
        (3 + 5) * selectorSpec.selectItem(_, _) >> selectorSpec

        cleanup:
        System.in = originalIn
    }
}
