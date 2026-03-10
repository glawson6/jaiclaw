package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import io.jclaw.shell.commands.setup.validation.DiscordTokenValidator;
import org.springframework.shell.component.flow.ComponentFlow;

public final class DiscordStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;
    private final DiscordTokenValidator validator;

    public DiscordStep(ComponentFlow.Builder flowBuilder, DiscordTokenValidator validator) {
        this.flowBuilder = flowBuilder;
        this.validator = validator;
    }

    @Override
    public String name() {
        return "Discord";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // Skip Discord in QuickStart mode
        if (!result.isManual()) {
            return true;
        }

        ComponentFlow enableFlow = flowBuilder.clone().reset()
                .withConfirmationInput("enable-discord")
                    .name("Set up Discord bot?")
                    .defaultValue(false)
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult enableResult = enableFlow.run();
        Boolean enabled = WizardStep.getOrNull(enableResult.getContext(), "enable-discord", Boolean.class);

        if (!Boolean.TRUE.equals(enabled)) {
            return true;
        }

        ComponentFlow tokenFlow = flowBuilder.clone().reset()
                .withStringInput("discord-bot-token")
                    .name("Discord Bot Token:")
                    .maskCharacter('*')
                    .and()
                .withStringInput("discord-app-id")
                    .name("Discord Application ID:")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult tokenResult = tokenFlow.run();
        String botToken = WizardStep.getOrNull(tokenResult.getContext(), "discord-bot-token", String.class);
        String appId = WizardStep.getOrNull(tokenResult.getContext(), "discord-app-id", String.class);

        if (botToken == null || botToken.isBlank()) {
            System.out.println("  Bot token is required. Skipping Discord setup.");
            return true;
        }

        // Validate
        System.out.print("  Validating Discord token... ");
        DiscordTokenValidator.ValidationResult validation = validator.validate(botToken);
        if (validation.valid()) {
            System.out.println("OK — " + validation.message());
        } else {
            System.out.println("FAILED: " + validation.message());
            System.out.println("  You can continue and fix the token later.");
        }

        result.setDiscord(new OnboardResult.DiscordConfig(botToken, appId, true));
        return true;
    }
}
