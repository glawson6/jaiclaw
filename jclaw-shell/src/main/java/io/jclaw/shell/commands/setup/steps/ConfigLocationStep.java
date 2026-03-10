package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import io.jclaw.shell.commands.setup.config.ConfigLocation;
import org.springframework.shell.component.flow.ComponentFlow;

import java.nio.file.Path;

public final class ConfigLocationStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public ConfigLocationStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "Config Location";
    }

    @Override
    public boolean execute(OnboardResult result) {
        Path defaultDir = ConfigLocation.defaultDir();
        Path cwd = Path.of("").toAbsolutePath();

        ComponentFlow flow = flowBuilder.clone().reset()
                .withSingleItemSelector("config-location")
                    .name("Where should configuration be saved?")
                    .selectItem("home", defaultDir + " (recommended, persists across projects)")
                    .selectItem("cwd", cwd + " (current working directory)")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult flowResult = flow.run();
        String selected = WizardStep.getOrNull(flowResult.getContext(), "config-location", String.class);

        if ("cwd".equals(selected)) {
            result.setConfigDir(cwd);
        } else {
            result.setConfigDir(defaultDir);
        }

        return true;
    }
}
