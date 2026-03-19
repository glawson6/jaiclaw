package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import org.springframework.shell.component.flow.ComponentFlow;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SecurityStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public SecurityStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "Security";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // QuickStart defaults to api-key with auto-generated key — skip prompts
        if (!result.isManual()) {
            result.setSecurityMode("api-key");
            return true;
        }

        Map<String, String> modes = new LinkedHashMap<>();
        modes.put("API Key (default)", "api-key");
        modes.put("JWT (token-based authentication)", "jwt");
        modes.put("None (disable security — development only)", "none");

        ComponentFlow flow = flowBuilder.clone().reset()
                .withSingleItemSelector("security-mode")
                    .name("Security mode:")
                    .selectItems(modes)
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult flowResult = flow.run();
        String mode = WizardStep.getOrNull(flowResult.getContext(), "security-mode", String.class);
        if (mode != null) {
            result.setSecurityMode(mode);
        }

        // Offer custom API key only in api-key mode
        if ("api-key".equals(result.securityMode())) {
            ComponentFlow keyFlow = flowBuilder.clone().reset()
                    .withStringInput("custom-api-key")
                        .name("Custom API key (leave blank to auto-generate):")
                        .defaultValue("")
                        .and()
                    .build();

            ComponentFlow.ComponentFlowResult keyResult = keyFlow.run();
            String customKey = WizardStep.getOrNull(keyResult.getContext(), "custom-api-key", String.class);
            if (customKey != null && !customKey.isBlank()) {
                result.setApiKey(customKey);
            }
        }

        return true;
    }
}
