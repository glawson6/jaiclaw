package io.jaiclaw.skill.creator;

import io.jaiclaw.agent.AgentRuntime;
import io.jaiclaw.agent.AgentRuntimeContext;
import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.config.JaiClawProperties;

import org.jline.reader.LineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@ShellComponent
public class SkillCreatorCommands {

    private static final Logger log = LoggerFactory.getLogger(SkillCreatorCommands.class);

    private final ObjectProvider<AgentRuntime> agentRuntimeProvider;
    private final ObjectProvider<SessionManager> sessionManagerProvider;
    private final ObjectProvider<JaiClawProperties> propertiesProvider;
    private final ObjectProvider<LineReader> lineReaderProvider;

    @Value("${skill-creator.output-dir:#{null}}")
    private String configuredOutputDir;

    public SkillCreatorCommands(
            ObjectProvider<AgentRuntime> agentRuntimeProvider,
            ObjectProvider<SessionManager> sessionManagerProvider,
            ObjectProvider<JaiClawProperties> propertiesProvider,
            ObjectProvider<LineReader> lineReaderProvider) {
        this.agentRuntimeProvider = agentRuntimeProvider;
        this.sessionManagerProvider = sessionManagerProvider;
        this.propertiesProvider = propertiesProvider;
        this.lineReaderProvider = lineReaderProvider;
    }

    @ShellMethod(value = "Interactively create a new JaiClaw skill", key = {"skill-create", "skill create"})
    public String create(
            @ShellOption(value = "--output-dir", defaultValue = ShellOption.NULL,
                    help = "Directory to write the skill to") String outputDir,
            @ShellOption(value = "--name", defaultValue = ShellOption.NULL,
                    help = "Skill name (optional — LLM will ask if not provided)") String name) {

        AgentRuntime runtime = agentRuntimeProvider.getIfAvailable();
        if (runtime == null) {
            return "No LLM configured. Set ANTHROPIC_API_KEY, OPENAI_API_KEY, or enable Ollama.";
        }

        LineReader reader = lineReaderProvider.getIfAvailable();
        if (reader == null) {
            return "Interactive mode requires a terminal with LineReader support.";
        }

        SessionManager sessionManager = sessionManagerProvider.getIfAvailable();
        if (sessionManager == null) {
            return "SessionManager not available.";
        }

        JaiClawProperties properties = propertiesProvider.getIfAvailable();
        String agentId = properties != null ? properties.agent().defaultAgent() : "default";
        String sessionKey = "skill-creator-" + System.currentTimeMillis();
        var session = sessionManager.getOrCreate(sessionKey, agentId);
        var context = new AgentRuntimeContext(agentId, sessionKey, session);

        // Build initial prompt
        var sb = new StringBuilder("I want to create a new JaiClaw skill.");
        if (name != null && !name.isBlank()) {
            sb.append(" The skill name should be: ").append(name);
        }
        String resolvedOutputDir = resolveOutputDir(outputDir);
        if (resolvedOutputDir != null) {
            sb.append(" Write the output to: ").append(resolvedOutputDir);
        } else {
            sb.append(" Please ask me where to write the output skill file.");
        }
        sb.append(" Guide me through the creation process step by step.");

        // Send initial message
        try {
            var response = runtime.run(sb.toString(), context).join();
            System.out.println("\n" + response.content() + "\n");
        } catch (Exception e) {
            return "Error starting conversation: " + e.getMessage();
        }

        // Conversational loop
        System.out.println("Type /save to generate and save the skill, /exit to quit.\n");
        while (true) {
            String input;
            try {
                input = reader.readLine("skill> ");
            } catch (Exception e) {
                break;
            }

            if (input == null || "/exit".equals(input.trim())) {
                return "Skill creation cancelled.";
            }

            String trimmed = input.trim();
            if (trimmed.isEmpty()) continue;

            if ("/save".equals(trimmed)) {
                return handleSave(runtime, context, resolvedOutputDir, reader);
            }

            try {
                var response = runtime.run(trimmed, context).join();
                System.out.println("\n" + response.content() + "\n");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }

        return "Skill creation ended.";
    }

    @ShellMethod(value = "Generate a skill from a YAML spec file", key = {"skill-generate", "skill generate"})
    public String generate(
            @ShellOption(value = "--spec", help = "Path to YAML spec file") String specPath,
            @ShellOption(value = "--output-dir", defaultValue = ShellOption.NULL,
                    help = "Directory to write the skill to") String outputDir) {

        AgentRuntime runtime = agentRuntimeProvider.getIfAvailable();
        if (runtime == null) {
            return "No LLM configured. Set ANTHROPIC_API_KEY, OPENAI_API_KEY, or enable Ollama.";
        }

        SessionManager sessionManager = sessionManagerProvider.getIfAvailable();
        if (sessionManager == null) {
            return "SessionManager not available.";
        }

        // Parse YAML spec
        Path specFile = Path.of(specPath);
        if (!Files.exists(specFile)) {
            return "Spec file not found: " + specPath;
        }

        SkillSpec spec;
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(Files.readString(specFile));
            spec = SkillSpec.fromYamlMap(map);
            spec.validate();
        } catch (IOException e) {
            return "Failed to read spec file: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "Invalid spec: " + e.getMessage();
        }

        // Resolve output directory
        String resolvedOutputDir = resolveOutputDir(outputDir);
        if (resolvedOutputDir == null) {
            resolvedOutputDir = System.getProperty("user.dir");
        }

        // Send to agent
        JaiClawProperties properties = propertiesProvider.getIfAvailable();
        String agentId = properties != null ? properties.agent().defaultAgent() : "default";
        String sessionKey = "skill-gen-" + System.currentTimeMillis();
        var session = sessionManager.getOrCreate(sessionKey, agentId);
        var context = new AgentRuntimeContext(agentId, sessionKey, session);

        System.out.println("Generating skill '" + spec.name() + "'...");

        try {
            var response = runtime.run(spec.toPrompt(), context).join();
            String content = extractSkillContent(response.content());
            String skillName = extractSkillName(content, spec.name());

            // Write to {outputDir}/{skillName}/SKILL.md
            Path skillDir = Path.of(resolvedOutputDir, skillName);
            Files.createDirectories(skillDir);
            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, content);

            return "Skill written to: " + skillFile.toAbsolutePath();
        } catch (IOException e) {
            return "Failed to write skill file: " + e.getMessage();
        } catch (Exception e) {
            return "Error generating skill: " + e.getMessage();
        }
    }

    private String handleSave(AgentRuntime runtime, AgentRuntimeContext context,
                              String outputDir, LineReader reader) {
        try {
            var response = runtime.run(
                    "Please output the final, complete SKILL.md file content now. " +
                    "Include the full YAML frontmatter and all body instructions. " +
                    "Wrap it in a markdown code block.",
                    context).join();

            String content = extractSkillContent(response.content());
            String skillName = extractSkillName(content, null);

            // Resolve output dir — ask user if still not available
            String resolvedDir = outputDir;
            if (resolvedDir == null) {
                try {
                    String input = reader.readLine("Output directory [" + System.getProperty("user.dir") + "]: ");
                    if (input != null && !input.trim().isEmpty()) {
                        resolvedDir = input.trim();
                    } else {
                        resolvedDir = System.getProperty("user.dir");
                    }
                } catch (Exception e) {
                    resolvedDir = System.getProperty("user.dir");
                }
            }

            if (skillName == null || skillName.isBlank()) {
                try {
                    String input = reader.readLine("Skill name: ");
                    if (input != null && !input.trim().isEmpty()) {
                        skillName = input.trim();
                    } else {
                        return "Cannot save without a skill name.";
                    }
                } catch (Exception e) {
                    return "Cannot determine skill name.";
                }
            }

            Path skillDir = Path.of(resolvedDir, skillName);
            Files.createDirectories(skillDir);
            Path skillFile = skillDir.resolve("SKILL.md");
            Files.writeString(skillFile, content);

            return "Skill written to: " + skillFile.toAbsolutePath();
        } catch (IOException e) {
            return "Failed to write skill file: " + e.getMessage();
        } catch (Exception e) {
            return "Error saving skill: " + e.getMessage();
        }
    }

    /**
     * Resolve output directory from CLI flag, configured default, or null.
     */
    private String resolveOutputDir(String cliOutputDir) {
        if (cliOutputDir != null && !cliOutputDir.isBlank()) {
            return cliOutputDir;
        }
        if (configuredOutputDir != null && !configuredOutputDir.isBlank()) {
            return configuredOutputDir;
        }
        return null;
    }

    /**
     * Extract SKILL.md content from LLM response.
     * Looks for fenced markdown block first, then raw frontmatter, then falls back to raw response.
     */
    static String extractSkillContent(String response) {
        if (response == null || response.isBlank()) return "";

        // Look for ```markdown ... ``` or ``` ... ``` fenced block
        int fenceStart = findFenceStart(response);
        if (fenceStart >= 0) {
            int contentStart = response.indexOf('\n', fenceStart);
            if (contentStart >= 0) {
                contentStart++; // skip newline
                int fenceEnd = response.indexOf("\n```", contentStart);
                if (fenceEnd >= 0) {
                    return response.substring(contentStart, fenceEnd).strip();
                }
            }
        }

        // Fallback: if starts with frontmatter delimiter, use as-is
        String stripped = response.strip();
        if (stripped.startsWith("---")) {
            return stripped;
        }

        // Last resort: raw response
        return stripped;
    }

    /**
     * Extract skill name from frontmatter.
     */
    static String extractSkillName(String content, String fallback) {
        if (content != null && content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 3) {
                String frontmatter = content.substring(3, endIdx);
                for (String line : frontmatter.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("name:")) {
                        String name = trimmed.substring(5).trim();
                        // Remove quotes if present
                        if ((name.startsWith("\"") && name.endsWith("\""))
                                || (name.startsWith("'") && name.endsWith("'"))) {
                            name = name.substring(1, name.length() - 1);
                        }
                        if (!name.isBlank()) return name;
                    }
                }
            }
        }
        return fallback;
    }

    private static int findFenceStart(String text) {
        int idx = text.indexOf("```markdown");
        if (idx >= 0) return idx;
        idx = text.indexOf("```yaml");
        if (idx >= 0) return idx;
        // Generic triple-backtick fence (but not closing fence)
        idx = text.indexOf("```");
        if (idx >= 0) return idx;
        return -1;
    }
}
