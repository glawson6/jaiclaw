package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import org.springframework.shell.component.flow.ComponentFlow;

public final class WelcomeStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public WelcomeStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "Welcome";
    }

    @Override
    public boolean execute(OnboardResult result) {
        System.out.println("""

                ╔══════════════════════════════════════════════╗
                ║           Welcome to JClaw Setup             ║
                ╚══════════════════════════════════════════════╝

                This wizard will help you configure:
                  • LLM provider (OpenAI, Anthropic, or Ollama)
                  • Messaging channels (Telegram, Slack, Discord)
                  • Gateway and server settings
                """);

        ComponentFlow flow = flowBuilder.clone().reset()
                .withConfirmationInput("security-ack")
                    .name("JClaw will store API keys in a local .env file. " +
                          "Never commit this file to version control. Continue?")
                    .defaultValue(true)
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult flowResult = flow.run();
        Boolean confirmed = WizardStep.getOrNull(flowResult.getContext(), "security-ack", Boolean.class);
        return Boolean.TRUE.equals(confirmed);
    }
}
