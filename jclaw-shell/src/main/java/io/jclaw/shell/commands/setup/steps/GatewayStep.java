package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import org.springframework.shell.component.flow.ComponentFlow;

public final class GatewayStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public GatewayStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "Gateway";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // Skip in QuickStart mode
        if (!result.isManual()) {
            return true;
        }

        ComponentFlow flow = flowBuilder.clone().reset()
                .withStringInput("server-port")
                    .name("Server port:")
                    .defaultValue("8080")
                    .and()
                .withStringInput("bind-address")
                    .name("Bind address:")
                    .defaultValue("0.0.0.0")
                    .and()
                .withStringInput("assistant-name")
                    .name("Assistant name:")
                    .defaultValue("JClaw")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult flowResult = flow.run();

        String port = WizardStep.getOrNull(flowResult.getContext(), "server-port", String.class);
        if (port != null && !port.isBlank()) {
            try {
                result.setServerPort(Integer.parseInt(port));
            } catch (NumberFormatException e) {
                System.out.println("  Invalid port, using default 8080");
            }
        }

        String bind = WizardStep.getOrNull(flowResult.getContext(), "bind-address", String.class);
        if (bind != null && !bind.isBlank()) {
            result.setBindAddress(bind);
        }

        String name = WizardStep.getOrNull(flowResult.getContext(), "assistant-name", String.class);
        if (name != null && !name.isBlank()) {
            result.setAssistantName(name);
        }

        return true;
    }
}
