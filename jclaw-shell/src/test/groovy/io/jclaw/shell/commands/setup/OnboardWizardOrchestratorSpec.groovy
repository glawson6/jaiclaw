package io.jclaw.shell.commands.setup

import io.jclaw.shell.commands.setup.config.EnvFileWriter
import io.jclaw.shell.commands.setup.config.YamlConfigWriter
import io.jclaw.shell.commands.setup.validation.DiscordTokenValidator
import io.jclaw.shell.commands.setup.validation.LlmConnectivityTester
import io.jclaw.shell.commands.setup.validation.SlackTokenValidator
import io.jclaw.shell.commands.setup.validation.TelegramTokenValidator
import org.springframework.shell.component.flow.ComponentFlow
import spock.lang.Specification

class OnboardWizardOrchestratorSpec extends Specification {

    ComponentFlow.Builder flowBuilder = Mock()
    LlmConnectivityTester llmTester = Mock()
    TelegramTokenValidator telegramValidator = Mock()
    SlackTokenValidator slackValidator = Mock()
    DiscordTokenValidator discordValidator = Mock()
    YamlConfigWriter yamlWriter = Mock()
    EnvFileWriter envWriter = Mock()

    OnboardWizardOrchestrator orchestrator = new OnboardWizardOrchestrator(
            flowBuilder, llmTester, telegramValidator, slackValidator,
            discordValidator, yamlWriter, envWriter)

    def "buildSteps returns all 13 wizard steps"() {
        when:
        def steps = orchestrator.buildSteps(new OnboardResult())

        then:
        steps.size() == 13
        steps[0].name() == "Welcome"
        steps[1].name() == "Flow Mode"
        steps[2].name() == "Existing Config"
        steps[3].name() == "LLM Provider"
        steps[4].name() == "Gateway"
        steps[5].name() == "Security"
        steps[6].name() == "Telegram"
        steps[7].name() == "Slack"
        steps[8].name() == "Discord"
        steps[9].name() == "Skills"
        steps[10].name() == "MCP Servers"
        steps[11].name() == "Config Location"
        steps[12].name() == "Finalization"
    }

    def "all steps implement WizardStep sealed interface"() {
        when:
        def steps = orchestrator.buildSteps(new OnboardResult())

        then:
        steps.every { it instanceof WizardStep }
    }
}
