package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.OnboardResult.ExistingConfigAction;
import io.jclaw.shell.commands.setup.WizardStep;
import io.jclaw.shell.commands.setup.config.ConfigLocation;
import org.springframework.shell.component.flow.ComponentFlow;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ExistingConfigStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public ExistingConfigStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "Existing Config";
    }

    @Override
    public boolean execute(OnboardResult result) {
        Path defaultDir = ConfigLocation.defaultDir();
        Path yamlFile = ConfigLocation.yamlFile(defaultDir);

        if (!Files.exists(yamlFile)) {
            result.setExistingConfigAction(ExistingConfigAction.NONE);
            return true;
        }

        System.out.println("\n  Existing configuration found: " + yamlFile);

        ComponentFlow flow = flowBuilder.clone().reset()
                .withSingleItemSelector("config-action")
                    .name("What would you like to do with the existing configuration?")
                    .selectItem("keep", "Keep — Exit wizard, use existing config")
                    .selectItem("modify", "Modify — Update existing config with new values")
                    .selectItem("reset", "Reset — Start fresh, overwrite existing config")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult flowResult = flow.run();
        String selected = WizardStep.getOrNull(flowResult.getContext(), "config-action", String.class);
        if (selected == null) {
            return false;
        }

        return switch (selected) {
            case "keep" -> {
                result.setExistingConfigAction(ExistingConfigAction.KEEP);
                System.out.println("  Keeping existing configuration. No changes made.");
                yield false; // abort wizard — config already exists
            }
            case "modify" -> {
                result.setExistingConfigAction(ExistingConfigAction.MODIFY);
                yield true;
            }
            case "reset" -> {
                result.setExistingConfigAction(ExistingConfigAction.RESET);
                yield true;
            }
            default -> false;
        };
    }
}
