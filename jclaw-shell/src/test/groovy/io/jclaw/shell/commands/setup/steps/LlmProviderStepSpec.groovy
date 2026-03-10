package io.jclaw.shell.commands.setup.steps

import io.jclaw.shell.commands.setup.OnboardResult
import io.jclaw.shell.commands.setup.validation.LlmConnectivityTester
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
        // no "provider" key set — will return null

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
}
