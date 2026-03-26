package io.jaiclaw.cli.architect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auto-configuration that registers CLI Architect tools into the JaiClaw ToolRegistry.
 * Activates when the JAR is on the classpath and a ToolRegistry bean exists.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnBean(ToolRegistry.class)
public class CliArchitectAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CliArchitectAutoConfiguration.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    public CliArchitectToolsRegistrar cliArchitectToolsRegistrar(ToolRegistry toolRegistry) {
        log.info("Registering CLI Architect tools into ToolRegistry");
        toolRegistry.register(new ScaffoldProjectTool());
        toolRegistry.register(new ParseOpenApiTool());
        toolRegistry.register(new ValidateSpecTool());
        toolRegistry.register(new FromOpenApiTool());
        return new CliArchitectToolsRegistrar();
    }

    /** Marker bean. */
    public static class CliArchitectToolsRegistrar {}

    // --- Tool implementations ---

    static class ScaffoldProjectTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "cli_scaffold_project",
                    "Generate a Spring Shell CLI project from a JSON spec. Provide the spec as a JSON string in the 'spec' parameter.",
                    "cli-architect",
                    """
                    {"type":"object","properties":{"spec":{"type":"string","description":"JSON spec for the CLI project"},"outputDir":{"type":"string","description":"Output directory path"}},"required":["spec"]}
                    """,
                    Set.of(ToolProfile.FULL)
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            try {
                String specJson = (String) parameters.get("spec");
                String outputDir = (String) parameters.getOrDefault("outputDir", context.workspaceDir());
                ProjectSpec spec = MAPPER.readValue(specJson, ProjectSpec.class);

                List<String> issues = SpecValidator.validate(spec);
                if (!issues.isEmpty()) {
                    return new ToolResult.Error("Spec validation failed: " + String.join("; ", issues));
                }

                Map<String, String> files = ProjectGenerator.generate(spec);
                Path outputPath = Path.of(outputDir);
                Files.createDirectories(outputPath);

                for (var entry : files.entrySet()) {
                    Path filePath = outputPath.resolve(entry.getKey());
                    Files.createDirectories(filePath.getParent());
                    Files.writeString(filePath, entry.getValue());
                }

                return new ToolResult.Success("Generated %d files in %s: %s".formatted(
                        files.size(), outputPath, String.join(", ", files.keySet())));
            } catch (Exception e) {
                return new ToolResult.Error("Failed to scaffold project: " + e.getMessage());
            }
        }
    }

    static class ParseOpenApiTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "cli_parse_openapi",
                    "Parse an OpenAPI/Swagger spec (JSON) and return structured endpoint and auth data.",
                    "cli-architect",
                    """
                    {"type":"object","properties":{"spec":{"type":"string","description":"OpenAPI spec as JSON string"}},"required":["spec"]}
                    """,
                    Set.of(ToolProfile.FULL)
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            try {
                String specJson = (String) parameters.get("spec");
                var result = OpenApiParser.parse(specJson);
                return new ToolResult.Success(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            } catch (Exception e) {
                return new ToolResult.Error("Failed to parse OpenAPI spec: " + e.getMessage());
            }
        }
    }

    static class ValidateSpecTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "cli_validate_spec",
                    "Validate a CLI project spec JSON and report missing or invalid fields.",
                    "cli-architect",
                    """
                    {"type":"object","properties":{"spec":{"type":"string","description":"Project spec as JSON string"}},"required":["spec"]}
                    """,
                    Set.of(ToolProfile.FULL)
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            try {
                String specJson = (String) parameters.get("spec");
                ProjectSpec spec = MAPPER.readValue(specJson, ProjectSpec.class);

                List<String> issues = SpecValidator.validate(spec);
                List<String> gaps = SpecValidator.findGaps(spec);

                var sb = new StringBuilder();
                if (issues.isEmpty()) {
                    sb.append("Spec is valid.");
                } else {
                    sb.append("Issues: ").append(String.join("; ", issues));
                }
                if (!gaps.isEmpty()) {
                    sb.append(" Fillable gaps: ").append(String.join(", ", gaps));
                }
                return new ToolResult.Success(sb.toString());
            } catch (Exception e) {
                return new ToolResult.Error("Failed to validate spec: " + e.getMessage());
            }
        }
    }

    static class FromOpenApiTool implements ToolCallback {
        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "cli_from_openapi",
                    "Fetch an OpenAPI spec from a URL or file path and generate a project spec JSON template.",
                    "cli-architect",
                    """
                    {"type":"object","properties":{"url":{"type":"string","description":"URL or file path to an OpenAPI spec"}},"required":["url"]}
                    """,
                    Set.of(ToolProfile.FULL)
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            try {
                String url = (String) parameters.get("url");
                String openapiJson = fetchOrRead(url);
                var result = OpenApiParser.parse(openapiJson);

                var spec = new ProjectSpec(
                        deriveNameFromTitle(result.title()),
                        ProjectMode.STANDALONE,
                        null, "com.example", null, "both",
                        new ProjectSpec.ApiSpec(result.title(), result.baseUrl(), url),
                        result.auth(), result.endpoints()
                );

                return new ToolResult.Success(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(spec));
            } catch (Exception e) {
                return new ToolResult.Error("Failed to generate spec from OpenAPI: " + e.getMessage());
            }
        }

        private String fetchOrRead(String urlOrPath) throws IOException {
            if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
                try {
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(urlOrPath))
                            .GET()
                            .build();
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    return response.body();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
            return Files.readString(Path.of(urlOrPath));
        }

        private String deriveNameFromTitle(String title) {
            if (title == null || title.isBlank()) return "api";
            return title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        }
    }
}
