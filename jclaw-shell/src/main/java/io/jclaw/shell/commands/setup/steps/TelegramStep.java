package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import io.jclaw.shell.commands.setup.validation.TelegramTokenValidator;
import org.springframework.shell.component.flow.ComponentFlow;

public final class TelegramStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;
    private final TelegramTokenValidator validator;

    public TelegramStep(ComponentFlow.Builder flowBuilder, TelegramTokenValidator validator) {
        this.flowBuilder = flowBuilder;
        this.validator = validator;
    }

    @Override
    public String name() {
        return "Telegram";
    }

    @Override
    public boolean execute(OnboardResult result) {
        ComponentFlow enableFlow = flowBuilder.clone().reset()
                .withConfirmationInput("enable-telegram")
                    .name("Set up Telegram bot?")
                    .defaultValue(false)
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult enableResult = enableFlow.run();
        Boolean enabled = WizardStep.getOrNull(enableResult.getContext(), "enable-telegram", Boolean.class);

        if (!Boolean.TRUE.equals(enabled)) {
            result.setTelegram(new OnboardResult.TelegramConfig(null, false));
            return true;
        }

        ComponentFlow tokenFlow = flowBuilder.clone().reset()
                .withStringInput("telegram-token")
                    .name("Enter your Telegram bot token (from @BotFather):")
                    .maskCharacter('*')
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult tokenResult = tokenFlow.run();
        String token = WizardStep.getOrNull(tokenResult.getContext(), "telegram-token", String.class);
        if (token == null || token.isBlank()) {
            System.out.println("  Telegram token is required. Skipping Telegram setup.");
            result.setTelegram(new OnboardResult.TelegramConfig(null, false));
            return true;
        }

        // Validate token
        System.out.print("  Validating Telegram token... ");
        TelegramTokenValidator.ValidationResult validation = validator.validate(token);
        if (validation.valid()) {
            System.out.println("OK — " + validation.message());
        } else {
            System.out.println("FAILED: " + validation.message());
            System.out.println("  You can continue and fix the token later.");
        }

        result.setTelegram(new OnboardResult.TelegramConfig(token, true));
        return true;
    }
}
