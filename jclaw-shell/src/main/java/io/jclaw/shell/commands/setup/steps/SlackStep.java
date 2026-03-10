package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import io.jclaw.shell.commands.setup.validation.SlackTokenValidator;
import org.springframework.shell.component.flow.ComponentFlow;

public final class SlackStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;
    private final SlackTokenValidator validator;

    public SlackStep(ComponentFlow.Builder flowBuilder, SlackTokenValidator validator) {
        this.flowBuilder = flowBuilder;
        this.validator = validator;
    }

    @Override
    public String name() {
        return "Slack";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // Skip Slack in QuickStart mode
        if (!result.isManual()) {
            return true;
        }

        ComponentFlow enableFlow = flowBuilder.clone().reset()
                .withConfirmationInput("enable-slack")
                    .name("Set up Slack bot?")
                    .defaultValue(false)
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult enableResult = enableFlow.run();
        Boolean enabled = WizardStep.getOrNull(enableResult.getContext(), "enable-slack", Boolean.class);

        if (!Boolean.TRUE.equals(enabled)) {
            return true;
        }

        ComponentFlow tokenFlow = flowBuilder.clone().reset()
                .withStringInput("slack-bot-token")
                    .name("Slack Bot Token (xoxb-...):")
                    .maskCharacter('*')
                    .and()
                .withStringInput("slack-signing-secret")
                    .name("Slack Signing Secret:")
                    .maskCharacter('*')
                    .and()
                .withStringInput("slack-app-token")
                    .name("Slack App Token for Socket Mode (xapp-..., leave blank for webhook mode):")
                    .defaultValue("")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult tokenResult = tokenFlow.run();
        String botToken = WizardStep.getOrNull(tokenResult.getContext(), "slack-bot-token", String.class);
        String signingSecret = WizardStep.getOrNull(tokenResult.getContext(), "slack-signing-secret", String.class);
        String appToken = WizardStep.getOrNull(tokenResult.getContext(), "slack-app-token", String.class);

        if (botToken == null || botToken.isBlank()) {
            System.out.println("  Bot token is required. Skipping Slack setup.");
            return true;
        }

        // Validate
        System.out.print("  Validating Slack token... ");
        SlackTokenValidator.ValidationResult validation = validator.validate(botToken);
        if (validation.valid()) {
            System.out.println("OK — " + validation.message());
        } else {
            System.out.println("FAILED: " + validation.message());
            System.out.println("  You can continue and fix the token later.");
        }

        result.setSlack(new OnboardResult.SlackConfig(botToken, signingSecret, appToken, true));
        return true;
    }
}
