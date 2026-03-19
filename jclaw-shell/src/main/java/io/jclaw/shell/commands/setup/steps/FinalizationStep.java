package io.jclaw.shell.commands.setup.steps;

import io.jclaw.shell.commands.setup.OnboardResult;
import io.jclaw.shell.commands.setup.WizardStep;
import io.jclaw.shell.commands.setup.config.ConfigLocation;
import io.jclaw.shell.commands.setup.config.EnvFileWriter;
import io.jclaw.shell.commands.setup.config.YamlConfigWriter;
import org.springframework.shell.component.flow.ComponentFlow;

import java.nio.file.Path;

public final class FinalizationStep implements WizardStep {

    private final ComponentFlow.Builder flowBuilder;
    private final YamlConfigWriter yamlWriter;
    private final EnvFileWriter envWriter;

    public FinalizationStep(ComponentFlow.Builder flowBuilder,
                           YamlConfigWriter yamlWriter,
                           EnvFileWriter envWriter) {
        this.flowBuilder = flowBuilder;
        this.yamlWriter = yamlWriter;
        this.envWriter = envWriter;
    }

    @Override
    public String name() {
        return "Finalization";
    }

    @Override
    public boolean execute(OnboardResult result) {
        // Preview
        System.out.println("\n  Configuration Preview");
        System.out.println("  " + "─".repeat(40));
        System.out.println("  Provider:    " + result.llmProvider());
        System.out.println("  Model:       " + result.llmModel());
        System.out.println("  Security:    " + result.securityMode()
                + ("api-key".equals(result.securityMode()) && result.apiKey() != null
                        ? " (custom key)" : "api-key".equals(result.securityMode())
                        ? " (auto-generate)" : ""));
        System.out.println("  Assistant:   " + result.assistantName());
        if (result.isManual()) {
            System.out.println("  Port:        " + result.serverPort());
            System.out.println("  Bind:        " + result.bindAddress());
        }
        if (result.telegram() != null && result.telegram().enabled()) {
            System.out.println("  Telegram:    enabled");
        }
        if (result.slack() != null && result.slack().enabled()) {
            System.out.println("  Slack:       enabled");
        }
        if (result.discord() != null && result.discord().enabled()) {
            System.out.println("  Discord:     enabled");
        }
        // Skills preview
        OnboardResult.SkillsConfig skills = result.skillsConfig();
        if (skills != null) {
            String skillsDisplay = skills.enabledBundled().contains("*")
                    ? "all bundled"
                    : String.join(", ", skills.enabledBundled());
            System.out.println("  Skills:      " + skillsDisplay);
            if (skills.workspaceDir() != null) {
                System.out.println("  Skills dir:  " + skills.workspaceDir());
            }
        }
        // MCP servers preview
        if (result.mcpServers() != null && !result.mcpServers().isEmpty()) {
            int count = result.mcpServers().size();
            String names = result.mcpServers().stream()
                    .map(OnboardResult.McpServerConfig::name)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            System.out.println("  MCP servers: " + count + " (" + names + ")");
        }
        System.out.println("  Config dir:  " + result.configDir());
        System.out.println();

        // Confirm write
        ComponentFlow confirmFlow = flowBuilder.clone().reset()
                .withConfirmationInput("confirm-write")
                    .name("Write configuration files?")
                    .defaultValue(true)
                    .and()
                .build();

        ComponentFlow.ComponentFlowResult confirmResult = confirmFlow.run();
        Boolean confirmed = WizardStep.getOrNull(confirmResult.getContext(), "confirm-write", Boolean.class);

        if (!Boolean.TRUE.equals(confirmed)) {
            System.out.println("  Configuration not saved.");
            return false;
        }

        // Write files
        try {
            yamlWriter.write(result);
            envWriter.write(result);

            Path yamlPath = ConfigLocation.yamlFile(result.configDir());
            Path envPath = ConfigLocation.envFile(result.configDir());

            System.out.println("\n  Files written:");
            System.out.println("    " + yamlPath);
            System.out.println("    " + envPath);

            System.out.println("""

                  Next steps:
                  ─────────────────────────────────────
                  1. Source your secrets:
                     source %s

                  2. Restart JClaw:
                     ./mvnw spring-boot:run -pl jclaw-shell

                  3. Verify with:  status / models / config
                  """.formatted(envPath));

        } catch (Exception e) {
            System.out.println("  Error writing configuration: " + e.getMessage());
            return false;
        }

        return true;
    }
}
