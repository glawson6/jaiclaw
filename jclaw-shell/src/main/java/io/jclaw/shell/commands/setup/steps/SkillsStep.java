package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import org.springframework.shell.component.flow.ComponentFlow;
import org.springframework.shell.component.flow.SelectItem;

import java.util.List;

/**
 * Wizard step for configuring which bundled skills to enable
 * and an optional workspace skills directory.
 */
public final class SkillsStep implements WizardStep {

    private static final List<String> BUNDLED_SKILLS = List.of(
            "coding", "web-research", "system-admin",
            "conversation", "summarize", "k8s-monitoring"
    );

    private final ComponentFlow.Builder flowBuilder;

    public SkillsStep(ComponentFlow.Builder flowBuilder) {
        this.flowBuilder = flowBuilder;
    }

    @Override
    public String name() {
        return "Skills";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // Quickstart: enable all bundled skills, no workspace dir
        if (!result.isManual()) {
            result.setSkillsConfig(new OnboardResult.SkillsConfig(List.of("*"), null));
            return true;
        }

        // Manual: let user pick skills
        List<SelectItem> items = BUNDLED_SKILLS.stream()
                .map(s -> SelectItem.of(s, s, true, true))
                .toList();

        ComponentFlow flow = flowBuilder.clone().reset()
                .withMultiItemSelector("bundled-skills")
                    .name("Select bundled skills to enable:")
                    .selectItems(items)
                    .and()
                .build();
        ComponentFlow.ComponentFlowResult flowResult = flow.run();

        @SuppressWarnings("unchecked")
        List<String> selected = WizardStep.getOrNull(flowResult.getContext(), "bundled-skills", List.class);

        List<String> enabledSkills;
        if (selected == null || selected.isEmpty()) {
            enabledSkills = List.of("*");
        } else if (selected.size() == BUNDLED_SKILLS.size()) {
            enabledSkills = List.of("*");
        } else {
            enabledSkills = List.copyOf(selected);
        }

        // Workspace skills directory
        ComponentFlow dirFlow = flowBuilder.clone().reset()
                .withStringInput("workspace-skills-dir")
                    .name("Workspace skills directory (blank for none):")
                    .defaultValue("")
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult dirResult = dirFlow.run();
        String workspaceDir = WizardStep.getOrNull(dirResult.getContext(), "workspace-skills-dir", String.class);
        if (workspaceDir != null && workspaceDir.isBlank()) {
            workspaceDir = null;
        }

        result.setSkillsConfig(new OnboardResult.SkillsConfig(enabledSkills, workspaceDir));
        return true;
    }
}
