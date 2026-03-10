package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.OnboardResult.FlowMode;
import io.jclaw.shell.commands.setup.WizardStep;
import org.springframework.shell.component.flow.ComponentFlow;

public final class FlowModeStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;

    public FlowModeStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "Flow Mode";
    }

    @Override
    public boolean execute(OnboardResult result) {
        ComponentFlow flow = flowBuilder.clone().reset()
                .withSingleItemSelector("flow-mode")
                    .name("Choose setup mode:")
                    .selectItem("quickstart", "QuickStart — LLM + optional Telegram, sensible defaults")
                    .selectItem("manual", "Manual — Full control over all settings")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult flowResult = flow.run();
        String selected = WizardStep.getOrNull(flowResult.getContext(), "flow-mode", String.class);
        if (selected == null) {
            return false;
        }
        result.setFlowMode("manual".equals(selected) ? FlowMode.MANUAL : FlowMode.QUICKSTART);
        return true;
    }
}
