package io.jaiclaw.shell.commands.setup.steps;

import io.jaiclaw.shell.commands.prompt.PromptProperties;
import io.jaiclaw.shell.commands.setup.OnboardResult;
import io.jaiclaw.shell.commands.setup.WizardStep;
import org.springframework.shell.component.flow.ComponentFlow;

/**
 * Optional UX step that asks the operator whether they'd like to customize the
 * REPL command prompt. Defaults to "no" so first-time users aren't slowed down.
 *
 * <p>When enabled, captures a free-form format string supporting the same
 * placeholders the runtime {@code PromptProvider} understands —
 * {@code ${identity}}, {@code ${profile}}, {@code ${agent}}, {@code ${model}},
 * {@code ${tenant}}. The selected value is written to the profile's
 * {@code application-local.yml} by the {@code YamlConfigWriter} during
 * finalization, alongside the rest of the wizard's output.
 */
public final class PromptStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public PromptStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "Prompt";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // QuickStart skips — keeps the fast path fast. Operators can still
        // run `prompt set` in the REPL anytime, or re-run setup in manual mode.
        if (!result.isManual()) {
            return true;
        }

        ComponentFlow offer = flowBuilder.clone().reset()
                .withConfirmationInput("prompt-customize")
                    .name("Customize the REPL command prompt?")
                    .defaultValue(false)
                    .and()
                .build();
        ComponentFlow.ComponentFlowResult offerResult = offer.run();
        Boolean enabled = WizardStep.getOrNull(offerResult.getContext(), "prompt-customize", Boolean.class);
        if (!Boolean.TRUE.equals(enabled)) {
            return true;
        }

        ComponentFlow formatFlow = flowBuilder.clone().reset()
                .withStringInput("prompt-format")
                    .name("Prompt format (vars: ${identity} ${profile} ${agent} ${model} ${tenant}):")
                    .defaultValue(PromptProperties.DEFAULT_FORMAT)
                    .and()
                .build();
        ComponentFlow.ComponentFlowResult formatResult = formatFlow.run();
        String format = WizardStep.getOrNull(formatResult.getContext(), "prompt-format", String.class);
        if (format != null && !format.isBlank()) {
            result.setPromptFormat(format);
        }
        return true;
    }
}
