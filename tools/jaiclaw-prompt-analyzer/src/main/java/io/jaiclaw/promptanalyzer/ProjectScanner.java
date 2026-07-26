package io.jaiclaw.promptanalyzer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import io.jaiclaw.core.skill.SkillDefinition;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.skills.SkillLoader;
import io.jaiclaw.tools.builtin.BuiltinTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans a JaiClaw Maven project directory and produces an {@link AnalysisReport}
 * estimating input token usage based on configuration.
 */
public class ProjectScanner {

    private static final Logger log = LoggerFactory.getLogger(ProjectScanner.class);
    private static final int DEFAULT_SYSTEM_PROMPT_TOKENS = 50;

    // Per-tool structural overhead: the API wraps each tool in JSON with keys
    // ("name", "description", "input_schema"), braces, quotes, etc.
    // Empirically ~80 chars per tool.
    private static final int TOOL_STRUCTURAL_OVERHEAD_CHARS = 80;
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(YAMLFactory.builder().build());

    private final SkillLoader skillLoader;

    public ProjectScanner() {
        this(new SkillLoader());
    }

    public ProjectScanner(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    /**
     * Analyze a JaiClaw project directory and produce a token usage report.
     *
     * @param projectPath root of the Maven project (containing pom.xml and src/)
     * @return analysis report with estimated token counts
     * @throws IOException if the project directory cannot be read
     */
    public AnalysisReport analyze(Path projectPath) throws IOException {
        Path appYml = projectPath.resolve("src/main/resources/application.yml");
        if (!Files.isRegularFile(appYml)) {
            throw new IOException("No application.yml found at " + appYml);
        }

        JsonNode root = YAML_MAPPER.readTree(Files.readString(appYml, StandardCharsets.UTF_8));
        String projectName = projectPath.getFileName().toString();
        List<String> warnings = new ArrayList<>();

        // 1. Parse configuration
        List<String> allowBundled = parseAllowBundled(root, warnings);
        String toolProfile = parseToolProfile(root);
        int systemPromptTokens = parseSystemPrompt(root, projectPath);

        // 2. Resolve skills
        List<SkillDefinition> skills = skillLoader.loadConfigured(allowBundled, null);
        int skillsTokens = skills.stream()
                .mapToInt(s -> estimateTokens(s.content()))
                .sum();
        List<String> skillNames = allowBundled.contains("*")
                ? List.of("*")
                : skills.stream().map(SkillDefinition::name).toList();

        // 3. Resolve built-in tools (filtered by tool profile)
        ToolProfile resolvedProfile = resolveToolProfile(toolProfile);
        List<ToolCallback> builtinTools = BuiltinTools.all().stream()
                .filter(t -> t.definition().isAvailableIn(resolvedProfile))
                .toList();
        int builtinToolsTokens = builtinTools.stream()
                .mapToInt(t -> {
                    io.jaiclaw.core.tool.ToolDefinition def = t.definition();
                    int charCount = def.name().length()
                            + def.description().length()
                            + def.inputSchema().length()
                            + TOOL_STRUCTURAL_OVERHEAD_CHARS;
                    return estimateTokens(charCount);
                })
                .sum();

        // 4. Detect plugin and custom tools by scanning source for ToolDefinition constructors
        SourceToolScanResult sourceScan = scanSourceForTools(projectPath);

        // 5. Calculate total (includes custom/plugin tool estimates when measurable)
        int estimatedTotal = systemPromptTokens + skillsTokens + builtinToolsTokens
                + sourceScan.estimatedTokens;

        return new AnalysisReport(
                projectName,
                systemPromptTokens,
                skillsTokens,
                skills.size(),
                skillNames,
                builtinToolsTokens,
                builtinTools.size(),
                sourceScan.pluginToolCount,
                sourceScan.customToolCount,
                sourceScan.estimatedTokens,
                estimatedTotal,
                toolProfile,
                List.copyOf(warnings)
        );
    }

    private ToolProfile resolveToolProfile(String profileName) {
        if (profileName == null) return ToolProfile.FULL;
        return switch (profileName.toUpperCase()) {
            case "NONE"      -> ToolProfile.NONE;
            case "MINIMAL"   -> ToolProfile.MINIMAL;
            case "CODING"    -> ToolProfile.CODING;
            case "MESSAGING" -> ToolProfile.MESSAGING;
            default          -> ToolProfile.FULL;
        };
    }

    private List<String> parseAllowBundled(JsonNode root, List<String> warnings) {
        JsonNode node = navigateConfigPath(root, "skills", "allow-bundled");
        if (node == null || node.isMissingNode()) {
            warnings.add("allow-bundled not configured \u2014 defaults to [\"*\"], loading all bundled skills (~26K tokens)");
            warnings.add("Consider setting jaiclaw.skills.allow-bundled: [] or whitelist specific skills");
            return List.of("*");
        }
        if (node.isArray()) {
            List<String> result = new ArrayList<>();
            node.forEach(n -> result.add(n.asText()));
            return result;
        }
        return List.of("*");
    }

    private String parseToolProfile(JsonNode root) {
        // Check first agent's tool profile; fall back to "full"
        JsonNode agents = navigateConfigPath(root, "agent", "agents");
        if (agents != null && agents.isObject()) {
            var it = agents.properties().iterator();
            if (it.hasNext()) {
                JsonNode firstAgent = it.next().getValue();
                JsonNode profile = navigatePath(firstAgent, "tools", "profile");
                if (profile != null && profile.isTextual()) {
                    return profile.asText();
                }
            }
        }
        return "full";
    }

    /**
     * Simulates what {@code SystemPromptBuilder.build()} produces to get an accurate
     * token estimate. The builder generates: "You are {name}, {description}.\n\n
     * Today's date is {date}.\n\n" plus optional additional instructions.
     */
    private int parseSystemPrompt(JsonNode root, Path projectPath) {
        // Start with the template text that SystemPromptBuilder always generates
        StringBuilder systemPrompt = new StringBuilder();

        // Identity section — mirrors SystemPromptBuilder.build()
        String identityName = "JaiClaw";
        String identityDesc = "Personal AI assistant";
        JsonNode identity = navigateConfigPath(root, "identity");
        if (identity != null) {
            JsonNode nameNode = identity.get("name");
            JsonNode descNode = identity.get("description");
            if (nameNode != null && nameNode.isTextual()) identityName = nameNode.asText();
            if (descNode != null && descNode.isTextual()) identityDesc = descNode.asText();
        }
        systemPrompt.append("You are ").append(identityName);
        if (!identityDesc.isBlank()) {
            systemPrompt.append(", ").append(identityDesc);
        }
        systemPrompt.append(".\n\n");

        // Date context
        systemPrompt.append("Today's date is 2026-01-01.\n\n");

        // Check for additional system prompt content
        JsonNode agents = navigateConfigPath(root, "agent", "agents");
        if (agents != null && agents.isObject()) {
            var it = agents.properties().iterator();
            if (it.hasNext()) {
                JsonNode firstAgent = it.next().getValue();

                // Nested config: system-prompt.content / system-prompt.source
                JsonNode spConfig = navigatePath(firstAgent, "system-prompt");
                if (spConfig != null) {
                    JsonNode content = spConfig.get("content");
                    if (content != null && content.isTextual() && !content.asText().isBlank()) {
                        systemPrompt.append(content.asText()).append('\n');
                    }
                    JsonNode source = spConfig.get("source");
                    if (source != null && source.isTextual()) {
                        String fileContent = readSystemPromptResource(projectPath, source.asText());
                        if (fileContent != null) {
                            systemPrompt.append(fileContent).append('\n');
                        }
                    }
                }

                // Flat key: system-prompt-path (e.g. "classpath:system-prompt.md")
                JsonNode spPath = firstAgent.get("system-prompt-path");
                if (spPath != null && spPath.isTextual()) {
                    String fileContent = readSystemPromptResource(projectPath, spPath.asText());
                    if (fileContent != null) {
                        systemPrompt.append(fileContent).append('\n');
                    }
                }
            }
        }

        return estimateTokens(systemPrompt.toString()) + DEFAULT_SYSTEM_PROMPT_TOKENS;
    }

    /**
     * Reads a system prompt resource. Supports:
     * <ul>
     *   <li>{@code classpath:filename} — resolves under src/main/resources/</li>
     *   <li>Plain filename — resolves under src/main/resources/</li>
     *   <li>Relative path — resolves under project root</li>
     * </ul>
     */
    private String readSystemPromptResource(Path projectPath, String source) {
        String path = source.startsWith("classpath:") ? source.substring("classpath:".length()) : source;
        Path promptFile = projectPath.resolve("src/main/resources").resolve(path);
        if (Files.isRegularFile(promptFile)) {
            try {
                return Files.readString(promptFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Failed to read system prompt file: {}", promptFile, e);
            }
        }
        return null;
    }

    /**
     * Scans project source for ToolDefinition constructors to estimate custom/plugin tool tokens.
     * Extracts string literal arguments from {@code new ToolDefinition(...)} calls.
     */
    private SourceToolScanResult scanSourceForTools(Path projectPath) {
        Path javaDir = projectPath.resolve("src/main/java");
        if (!Files.isDirectory(javaDir)) {
            return new SourceToolScanResult(0, 0, 0);
        }

        int pluginToolCount = 0;
        int customToolCount = 0;
        int totalChars = 0;

        try (Stream<Path> files = Files.walk(javaDir)) {
            List<Path> javaFiles = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            for (Path file : javaFiles) {
                String content = Files.readString(file, StandardCharsets.UTF_8);

                // Count ToolDefinition constructor invocations to find tools
                // Each "new ToolDefinition(" represents a tool
                int toolDefCount = countOccurrences(content, "new ToolDefinition(");
                if (toolDefCount == 0) continue;

                boolean isPlugin = content.contains("implements JaiClawPlugin");
                boolean isCallback = content.contains("implements ToolCallback")
                        || (content.contains("@Bean") && content.contains("ToolCallback"));

                if (isPlugin) {
                    pluginToolCount += toolDefCount;
                } else if (isCallback) {
                    customToolCount += toolDefCount;
                } else {
                    // Tools defined in helper classes (e.g. inner classes extending AbstractBuiltinTool)
                    pluginToolCount += toolDefCount;
                }

                // Estimate token contribution by measuring string literals in the file
                // that appear near ToolDefinition constructors. We use a simpler heuristic:
                // sum all JSON-schema-like text blocks (triple-quoted strings containing "type")
                totalChars += measureToolDefinitionChars(content);
            }
        } catch (IOException e) {
            log.warn("Failed to scan source directory for tools", e);
        }

        int estimatedTokens = totalChars > 0
                ? estimateTokens(totalChars + (pluginToolCount + customToolCount) * TOOL_STRUCTURAL_OVERHEAD_CHARS)
                : 0;
        return new SourceToolScanResult(pluginToolCount, customToolCount, estimatedTokens);
    }

    /**
     * Estimates the char count contributed by ToolDefinition arguments in a source file.
     * Sums the lengths of: tool name strings, description strings, and inputSchema text blocks.
     */
    private int measureToolDefinitionChars(String source) {
        int total = 0;
        // Find all "new ToolDefinition(" positions and extract the string arguments
        int searchFrom = 0;
        while (true) {
            int pos = source.indexOf("new ToolDefinition(", searchFrom);
            if (pos < 0) break;
            searchFrom = pos + 1;

            // Extract the constructor call region — find matching paren
            int start = pos + "new ToolDefinition(".length();
            int depth = 1;
            int end = start;
            while (end < source.length() && depth > 0) {
                char c = source.charAt(end);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                end++;
            }

            String args = source.substring(start, Math.min(end - 1, source.length()));

            // Sum all string literal content (both regular "..." and text blocks """...""")
            total += measureStringLiterals(args);
        }
        return total;
    }

    /**
     * Measures the total char count of all string literals in the given code fragment.
     */
    private int measureStringLiterals(String code) {
        int total = 0;
        int i = 0;
        while (i < code.length()) {
            if (i + 2 < code.length() && code.charAt(i) == '"'
                    && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"') {
                // Text block """..."""
                int start = i + 3;
                int end = code.indexOf("\"\"\"", start);
                if (end > start) {
                    total += end - start;
                    i = end + 3;
                } else {
                    break;
                }
            } else if (code.charAt(i) == '"') {
                // Regular string "..."
                int start = i + 1;
                int end = start;
                while (end < code.length()) {
                    char c = code.charAt(end);
                    if (c == '\\') {
                        end += 2;
                    } else if (c == '"') {
                        break;
                    } else {
                        end++;
                    }
                }
                if (end < code.length()) {
                    total += end - start;
                    i = end + 1;
                } else {
                    break;
                }
            } else {
                i++;
            }
        }
        return total;
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) >= 0) {
            count++;
            idx += search.length();
        }
        return count;
    }

    record SourceToolScanResult(int pluginToolCount, int customToolCount, int estimatedTokens) {}

    /**
     * Navigates a config path under the canonical {@code jaiclaw} root.
     *
     * <p>0.8.0 P3.5: the legacy {@code jclaw}-root fallback was removed
     * as part of the naming consolidation. Projects that ship an
     * {@code application.yml} with {@code jclaw:} as the root key must
     * rename it to {@code jaiclaw:}.
     */
    private JsonNode navigateConfigPath(JsonNode root, String... subKeys) {
        return navigatePath(root, prepend("jaiclaw", subKeys));
    }

    private String[] prepend(String first, String... rest) {
        String[] result = new String[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    private JsonNode navigatePath(JsonNode node, String... keys) {
        JsonNode current = node;
        for (String key : keys) {
            if (current == null || !current.has(key)) {
                return null;
            }
            current = current.get(key);
        }
        return current;
    }

    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return estimateTokens(text.length());
    }

    static int estimateTokens(int charCount) {
        return Math.max(1, (charCount + 2) / 4);
    }
}
