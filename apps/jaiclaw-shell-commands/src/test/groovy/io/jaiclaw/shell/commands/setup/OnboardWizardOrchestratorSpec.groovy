package io.jaiclaw.shell.commands.setup

import io.jaiclaw.config.JaiClawProperties
import io.jaiclaw.config.ModelsProperties
import io.jaiclaw.config.ModelsProperties.ModelProviderConfig
import io.jaiclaw.shell.commands.setup.config.EnvFileWriter
import io.jaiclaw.shell.commands.setup.config.YamlConfigWriter
import io.jaiclaw.shell.commands.setup.validation.DiscordTokenValidator
import io.jaiclaw.shell.commands.setup.validation.LlmConnectivityTester
import io.jaiclaw.shell.commands.setup.validation.SlackTokenValidator
import io.jaiclaw.shell.commands.setup.validation.TelegramTokenValidator
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
    JaiClawProperties jaiClawProperties = JaiClawProperties.builder()
            .models(new ModelsProperties(Map.of(
                    "openai", ModelProviderConfig.builder()
                            .displayName("OpenAI")
                            .fallbackModel("gpt-4o-mini")
                            .wizardModels(["gpt-4o", "gpt-4o-mini"])
                            .build(),
                    "anthropic", ModelProviderConfig.builder()
                            .displayName("Anthropic")
                            .fallbackModel("claude-haiku-4-5-20251001")
                            .wizardModels(["claude-sonnet-4-6"])
                            .build()
            )))
            .build()

    OnboardWizardOrchestrator orchestrator = new OnboardWizardOrchestrator(
            flowBuilder, llmTester, telegramValidator, slackValidator,
            discordValidator, yamlWriter, envWriter, jaiClawProperties)

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
