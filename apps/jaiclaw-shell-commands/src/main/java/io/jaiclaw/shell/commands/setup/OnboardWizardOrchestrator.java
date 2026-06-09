package io.jaiclaw.shell.commands.setup;

import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.shell.commands.setup.config.EnvFileWriter;
import io.jaiclaw.shell.commands.setup.config.YamlConfigWriter;
import io.jaiclaw.shell.commands.setup.steps.*;
import io.jaiclaw.shell.commands.setup.validation.DiscordTokenValidator;
import io.jaiclaw.shell.commands.setup.validation.LlmConnectivityTester;
import io.jaiclaw.shell.commands.setup.validation.SlackTokenValidator;
import io.jaiclaw.shell.commands.setup.validation.TelegramTokenValidator;

import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OnboardWizardOrchestrator {

    private final ComponentFlow.Builder flowBuilder;
    private final LlmConnectivityTester llmTester;
    private final TelegramTokenValidator telegramValidator;
    private final SlackTokenValidator slackValidator;
    private final DiscordTokenValidator discordValidator;
    private final YamlConfigWriter yamlWriter;
    private final EnvFileWriter envWriter;
    private final JaiClawProperties jaiClawProperties;

    public OnboardWizardOrchestrator(ComponentFlow.Builder flowBuilder,
                                     LlmConnectivityTester llmTester,
                                     TelegramTokenValidator telegramValidator,
                                     SlackTokenValidator slackValidator,
                                     DiscordTokenValidator discordValidator,
                                     YamlConfigWriter yamlWriter,
                                     EnvFileWriter envWriter,
                                     JaiClawProperties jaiClawProperties) {
        this.flowBuilder = flowBuilder;
        this.llmTester = llmTester;
        this.telegramValidator = telegramValidator;
        this.slackValidator = slackValidator;
        this.discordValidator = discordValidator;
        this.yamlWriter = yamlWriter;
        this.envWriter = envWriter;
        this.jaiClawProperties = jaiClawProperties;
    }

    public String run() {
        OnboardResult result = new OnboardResult();
        for (WizardStep step : buildSteps(result)) {
            if (!step.execute(result)) {
                return "Onboarding cancelled.";
            }
        }
        return "Onboarding complete! Restart JaiClaw to apply your new configuration.";
    }

    List<WizardStep> buildSteps(OnboardResult result) {
        List<WizardStep> steps = new ArrayList<>();
        steps.add(new WelcomeStep(flowBuilder));
        steps.add(new FlowModeStep(flowBuilder));
        steps.add(new ExistingConfigStep(flowBuilder));
        steps.add(new LlmProviderStep(flowBuilder, llmTester, jaiClawProperties.models()));

        // Gateway only in manual mode — deferred check in step itself
        steps.add(new GatewayStep(flowBuilder));

        // Security mode (api-key / jwt / none)
        steps.add(new SecurityStep(flowBuilder));

        // Channel steps
        steps.add(new TelegramStep(flowBuilder, telegramValidator));
        steps.add(new SlackStep(flowBuilder, slackValidator));
        steps.add(new DiscordStep(flowBuilder, discordValidator));

        // Skills and MCP steps
        steps.add(new SkillsStep(flowBuilder));
        steps.add(new McpServersStep(flowBuilder));

        // Config location and finalization
        steps.add(new ConfigLocationStep(flowBuilder));
        steps.add(new FinalizationStep(flowBuilder, yamlWriter, envWriter));

        return steps;
    }
}
