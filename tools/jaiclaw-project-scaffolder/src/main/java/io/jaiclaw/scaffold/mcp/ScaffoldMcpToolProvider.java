package io.jaiclaw.scaffold.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.jaiclaw.core.mcp.McpToolDefinition;
import io.jaiclaw.core.mcp.McpToolProvider;
import io.jaiclaw.core.mcp.McpToolResult;
import io.jaiclaw.core.tenant.TenantContext;
import io.jaiclaw.scaffold.KnownModules;
import io.jaiclaw.scaffold.ProjectGenerator;
import io.jaiclaw.scaffold.ProjectManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * MCP tool provider that exposes project scaffolding tools to external MCP clients.
 *
 * <p>Exposes three tools:
 * <ul>
 *   <li>{@code scaffold_project} — generates a complete standalone project from a YAML manifest string</li>
 *   <li>{@code validate_manifest} — validates a manifest YAML without generating anything</li>
 *   <li>{@code list_options} — lists valid values for extensions, channels, providers, etc.</li>
 * </ul>
 */
public class ScaffoldMcpToolProvider implements McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldMcpToolProvider.class);
    private static final String SERVER_NAME = "project-scaffolder";

    private static final String SCAFFOLD_TOOL = "scaffold_project";
    private static final String VALIDATE_TOOL = "validate_manifest";
    private static final String LIST_OPTIONS_TOOL = "list_options";

    private static final String SCAFFOLD_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "manifest_yaml": {
                  "type": "string",
                  "description": "Complete jaiclaw-manifest.yml content as a YAML string"
                },
                "output_dir": {
                  "type": "string",
                  "description": "Parent directory where the project will be created. Defaults to current working directory.",
                  "default": "."
                }
              },
              "required": ["manifest_yaml"]
            }
            """;

    private static final String VALIDATE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "manifest_yaml": {
                  "type": "string",
                  "description": "Complete jaiclaw-manifest.yml content as a YAML string to validate"
                }
              },
              "required": ["manifest_yaml"]
            }
            """;

    private static final String LIST_OPTIONS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "category": {
                  "type": "string",
                  "description": "Category to list options for: extensions, channels, providers, archetypes, tool-profiles, security-modes, prompt-strategies, parent-modes",
                  "enum": ["extensions", "channels", "providers", "archetypes", "tool-profiles", "security-modes", "prompt-strategies", "parent-modes"]
                }
              },
              "required": ["category"]
            }
            """;

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;
    private final ProjectGenerator generator;

    public ScaffoldMcpToolProvider(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.generator = new ProjectGenerator();
    }

    @Override
    public String getServerName() {
        return SERVER_NAME;
    }

    @Override
    public String getServerDescription() {
        return "Generate standalone JaiClaw Maven projects from YAML manifests";
    }

    @Override
    public List<McpToolDefinition> getTools() {
        return List.of(
                new McpToolDefinition(
                        SCAFFOLD_TOOL,
                        "Generate a complete standalone JaiClaw Maven project from a YAML manifest. "
                                + "Returns the path to the generated project and a summary of generated files.",
                        SCAFFOLD_SCHEMA
                ),
                new McpToolDefinition(
                        VALIDATE_TOOL,
                        "Validate a jaiclaw-manifest.yml without generating any files. "
                                + "Returns validation result with any errors found.",
                        VALIDATE_SCHEMA
                ),
                new McpToolDefinition(
                        LIST_OPTIONS_TOOL,
                        "List valid values for manifest fields like extensions, channels, AI providers, "
                                + "archetypes, tool profiles, security modes, and prompt strategies.",
                        LIST_OPTIONS_SCHEMA
                )
        );
    }

    @Override
    public McpToolResult execute(String toolName, Map<String, Object> args, TenantContext tenant) {
        return switch (toolName) {
            case SCAFFOLD_TOOL -> executeScaffold(args);
            case VALIDATE_TOOL -> executeValidate(args);
            case LIST_OPTIONS_TOOL -> executeListOptions(args);
            default -> McpToolResult.error("Unknown tool: " + toolName);
        };
    }

    private McpToolResult executeScaffold(Map<String, Object> args) {
        String manifestYaml = requireString(args, "manifest_yaml");
        if (manifestYaml == null) {
            return McpToolResult.error("Missing required parameter: manifest_yaml");
        }

        String outputDir = stringOrDefault(args, "output_dir", ".");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = yamlMapper.readValue(manifestYaml, Map.class);
            ProjectManifest manifest = ProjectManifest.fromYamlMap(yamlMap);
            manifest.validate();

            Path projectPath = generator.generate(manifest, Path.of(outputDir));

            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "status", "success",
                    "project_path", projectPath.toAbsolutePath().toString(),
                    "artifact_id", manifest.artifactId(),
                    "archetype", manifest.archetype().name().toLowerCase(),
                    "ai_provider", manifest.aiProvider().primary(),
                    "extensions", manifest.extensions(),
                    "channels", manifest.channels(),
                    "custom_tools", manifest.customTools().stream().map(t -> t.name()).toList()
            ));
            return McpToolResult.success(json);
        } catch (IllegalArgumentException e) {
            return McpToolResult.error("Invalid manifest: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to scaffold project", e);
            return McpToolResult.error("Failed to generate project: " + e.getMessage());
        }
    }

    private McpToolResult executeValidate(Map<String, Object> args) {
        String manifestYaml = requireString(args, "manifest_yaml");
        if (manifestYaml == null) {
            return McpToolResult.error("Missing required parameter: manifest_yaml");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = yamlMapper.readValue(manifestYaml, Map.class);
            ProjectManifest manifest = ProjectManifest.fromYamlMap(yamlMap);
            manifest.validate();

            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "valid", true,
                    "name", manifest.name(),
                    "archetype", manifest.archetype().name().toLowerCase(),
                    "ai_provider", manifest.aiProvider().primary(),
                    "extensions_count", manifest.extensions().size(),
                    "channels_count", manifest.channels().size(),
                    "custom_tools_count", manifest.customTools().size()
            ));
            return McpToolResult.success(json);
        } catch (IllegalArgumentException e) {
            try {
                String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "valid", false,
                        "error", e.getMessage()
                ));
                return McpToolResult.success(json);
            } catch (Exception ex) {
                return McpToolResult.error("Validation error: " + e.getMessage());
            }
        } catch (Exception e) {
            return McpToolResult.error("Failed to parse manifest YAML: " + e.getMessage());
        }
    }

    private McpToolResult executeListOptions(Map<String, Object> args) {
        String category = requireString(args, "category");
        if (category == null) {
            return McpToolResult.error("Missing required parameter: category");
        }

        Object options = switch (category) {
            case "extensions" -> sorted(KnownModules.EXTENSIONS);
            case "channels" -> sorted(KnownModules.CHANNELS);
            case "providers" -> sorted(KnownModules.AI_PROVIDERS);
            case "archetypes" -> List.of("gateway", "embabel", "camel", "comprehensive", "minimal");
            case "tool-profiles" -> sorted(KnownModules.TOOL_PROFILES);
            case "security-modes" -> sorted(KnownModules.SECURITY_MODES);
            case "prompt-strategies" -> sorted(KnownModules.PROMPT_STRATEGIES);
            case "parent-modes" -> sorted(KnownModules.PARENT_MODES);
            default -> null;
        };

        if (options == null) {
            return McpToolResult.error("Unknown category: " + category
                    + ". Valid: extensions, channels, providers, archetypes, tool-profiles, security-modes, prompt-strategies, parent-modes");
        }

        try {
            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                    Map.of("category", category, "options", options));
            return McpToolResult.success(json);
        } catch (Exception e) {
            return McpToolResult.error("Failed to serialize options: " + e.getMessage());
        }
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null || value.toString().isBlank()) return null;
        return value.toString();
    }

    private static String stringOrDefault(Map<String, Object> args, String key, String defaultValue) {
        Object value = args.get(key);
        return (value != null && !value.toString().isBlank()) ? value.toString() : defaultValue;
    }

    private static List<String> sorted(java.util.Set<String> set) {
        return set.stream().sorted().toList();
    }
}
