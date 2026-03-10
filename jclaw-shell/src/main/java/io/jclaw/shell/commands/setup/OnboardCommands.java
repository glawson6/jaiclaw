package io.jclaw.shell.commands.setup;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

@ShellComponent
public class OnboardCommands {

    private final OnboardWizardOrchestrator orchestrator;

    public OnboardCommands(OnboardWizardOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @ShellMethod(key = "onboard", value = "Interactive setup wizard for JClaw configuration")
    public String onboard() {
        return orchestrator.run();
    }
}
