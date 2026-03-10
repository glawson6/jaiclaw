package io.jclaw.shell.commands.setup.steps

import io.jclaw.shell.commands.setup.OnboardResult
import io.jclaw.shell.commands.setup.validation.TelegramTokenValidator
import org.springframework.shell.component.flow.ComponentFlow
import org.springframework.shell.component.context.BaseComponentContext
import spock.lang.Specification

class TelegramStepSpec extends Specification {

    def "name returns Telegram"() {
        given:
        def step = new TelegramStep(Mock(ComponentFlow.Builder), Mock(TelegramTokenValidator))

        expect:
        step.name() == "Telegram"
    }

    def "sets disabled telegram config when user declines"() {
        given:
        def flowBuilder = Mock(ComponentFlow.Builder)
        def validator = Mock(TelegramTokenValidator)
        def step = new TelegramStep(flowBuilder, validator)
        def result = new OnboardResult()

        def flow = Mock(ComponentFlow)
        def flowResult = Mock(ComponentFlow.ComponentFlowResult)
        def context = new BaseComponentContext()
        context.put("enable-telegram", false)

        flowBuilder.clone() >> flowBuilder
        flowBuilder.reset() >> flowBuilder
        flowBuilder.withConfirmationInput(_) >> { Mock(org.springframework.shell.component.flow.ConfirmationInputSpec) {
            name(_) >> it
            defaultValue(_) >> it
            and() >> flowBuilder
        }}
        flowBuilder.build() >> flow
        flow.run() >> flowResult
        flowResult.getContext() >> context

        when:
        def ok = step.execute(result)

        then:
        ok
        result.telegram() != null
        !result.telegram().enabled()
    }
}
