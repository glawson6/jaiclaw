package io.jaiclaw.shell.commands.prompt;

import io.jaiclaw.shell.commands.setup.config.YamlConfigWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Shell-facing commands for inspecting and customizing the REPL prompt.
 *
 * <ul>
 *   <li>{@code prompt} — show the current format string + a rendered preview.</li>
 *   <li>{@code prompt set <format>} — persist a new format string to the active
 *       profile's {@code application-local.yml} and update the in-process
 *       {@link org.springframework.core.env.Environment} so the change takes
 *       effect immediately, without restarting the REPL.</li>
 * </ul>
 *
 * <p>Persistence target is computed from {@code JAICLAW_PROFILE_DIR} (env), with
 * a fallback to {@code ~/.jaiclaw/profiles/default/application-local.yml}.
 */
@ShellComponent
public class PromptCommands {

    private static final String LIVE_SOURCE = "jaiClawPromptLive";

    private final PromptProperties promptProperties;
    private final YamlConfigWriter writer;
    private final ConfigurableEnvironment environment;
    private final String profileDirOverride;

    public PromptCommands(PromptProperties promptProperties,
                          YamlConfigWriter writer,
                          ConfigurableEnvironment environment,
                          @Value("${JAICLAW_PROFILE_DIR:}") String profileDirOverride) {
        this.promptProperties = promptProperties;
        this.writer = writer;
        this.environment = environment;
        this.profileDirOverride = profileDirOverride;
    }

    @ShellMethod(key = "prompt", value = "Show the REPL prompt format and a rendered preview")
    public String prompt() {
        String format = currentFormat();
        return String.format("Format:  %s%nPreview: %s%nFile:    %s",
                format, renderPreview(format), localYaml());
    }

    @ShellMethod(key = "prompt-set", value = "Set the REPL prompt format and persist it to the active profile's application-local.yml")
    public String set(@ShellOption(help = "Prompt format string. Supports ${identity}, ${profile}, ${agent}, ${model}, ${tenant}.") String format) {
        if (format == null || format.isBlank()) {
            return "Format must not be blank. See 'help prompt-set'.";
        }
        Path file = localYaml();
        try {
            writer.merge(file, "jaiclaw.shell.prompt.format", format);
        } catch (IOException e) {
            return "Failed to write " + file + ": " + e.getMessage();
        }
        applyLive(format);
        return String.format("Saved.%nFormat:  %s%nPreview: %s%nFile:    %s",
                format, renderPreview(format), file);
    }

    private String currentFormat() {
        String live = JaiClawPromptProvider.rawProperty(environment, "jaiclaw.shell.prompt.format");
        return (live != null && !live.isBlank()) ? live : promptProperties.format();
    }

    private String renderPreview(String format) {
        // The provider is the source of truth for substitution; calling it
        // here would render the LIVE format, not the candidate. Substitute
        // directly via the same helper for a faithful preview. Use raw
        // property reads to avoid Spring's placeholder resolution corrupting
        // the candidate format string.
        Map<String, java.util.function.Supplier<String>> vars = new HashMap<>();
        vars.put("identity", () -> JaiClawPromptProvider.rawProperty(environment, "jaiclaw.identity.name"));
        vars.put("agent", () -> JaiClawPromptProvider.rawProperty(environment, "jaiclaw.agent.default-agent"));
        vars.put("profile", PromptCommands::previewProfile);
        vars.put("model", () -> {
            String m = JaiClawPromptProvider.rawProperty(environment, "spring.ai.anthropic.chat.options.model");
            return m != null ? m : JaiClawPromptProvider.rawProperty(environment, "spring.ai.openai.chat.options.model");
        });
        vars.put("tenant", () -> "");
        return JaiClawPromptProvider.substitute(format, vars);
    }

    private static String previewProfile() {
        String dir = System.getenv("JAICLAW_PROFILE_DIR");
        if (dir != null && !dir.isBlank()) {
            Path p = Paths.get(dir).getFileName();
            return p != null ? p.toString() : "default";
        }
        return "default";
    }

    private Path localYaml() {
        Path dir;
        if (profileDirOverride != null && !profileDirOverride.isBlank()) {
            dir = Paths.get(profileDirOverride);
        } else {
            dir = Paths.get(System.getProperty("user.home"), ".jaiclaw", "profiles", "default");
        }
        return dir.resolve("application-local.yml");
    }

    private void applyLive(String format) {
        // Push the new format into a dedicated MapPropertySource at the top of
        // the environment so the running provider picks it up on the next
        // prompt render. Survives until the JVM exits; the file write persists
        // across restarts.
        Map<String, Object> live = new HashMap<>();
        live.put("jaiclaw.shell.prompt.format", format);
        MapPropertySource existing = (MapPropertySource) environment.getPropertySources().get(LIVE_SOURCE);
        if (existing != null) {
            existing.getSource().put("jaiclaw.shell.prompt.format", format);
        } else {
            environment.getPropertySources().addFirst(new MapPropertySource(LIVE_SOURCE, live));
        }
    }
}
