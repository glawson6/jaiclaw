package io.jaiclaw.cli.architect;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Spring Shell commands for the REST CLI Architect.
 * Provides scaffold, validate, from-openapi, and interactive commands.
 */
@ShellComponent
public class JRestCliCommands {

    private static final Logger log = LoggerFactory.getLogger(JRestCliCommands.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ShellMethod(value = "Generate a CLI project from a JSON spec file", key = "scaffold")
    public String scaffold(
            @ShellOption("--spec") String specPath,
            @ShellOption(value = "--fill", defaultValue = "false") boolean fill) throws IOException {

        String json = Files.readString(Path.of(specPath));
        ProjectSpec spec = MAPPER.readValue(json, ProjectSpec.class);

        // Validate
        List<String> issues = SpecValidator.validate(spec);
        if (!issues.isEmpty() && !fill) {
            return "Spec validation failed:\n" + String.join("\n", issues.stream().map(i -> "  - " + i).toList());
        }

        if (fill) {
            List<String> gaps = SpecValidator.findGaps(spec);
            if (!gaps.isEmpty()) {
                return "The following fields need to be filled (use interactive mode or provide them in the spec):\n"
                        + String.join("\n", gaps.stream().map(g -> "  - " + g).toList());
            }
        }

        // If spec has an OpenAPI URL and no endpoints, parse them
        if ((spec.endpoints() == null || spec.endpoints().isEmpty())
                && spec.api() != null && spec.api().openapiSpec() != null && !spec.api().openapiSpec().isBlank()) {
            String openapiJson = fetchOrRead(spec.api().openapiSpec());
            var parseResult = OpenApiParser.parse(openapiJson);
            spec = mergeOpenApiResult(spec, parseResult);
        }

        // Generate
        Map<String, String> files = ProjectGenerator.generate(spec);

        // Write files
        String outputDir = spec.outputDir() != null ? spec.outputDir() : ".";
        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        for (var entry : files.entrySet()) {
            Path filePath = outputPath.resolve(entry.getKey());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, entry.getValue());
            log.info("Created: {}", filePath);
        }

        return "Generated %d files in %s".formatted(files.size(), outputPath.toAbsolutePath());
    }

    @ShellMethod(value = "Validate a JSON spec file and report issues", key = "validate")
    public String validate(@ShellOption("--spec") String specPath) throws IOException {
        String json = Files.readString(Path.of(specPath));
        ProjectSpec spec = MAPPER.readValue(json, ProjectSpec.class);

        List<String> issues = SpecValidator.validate(spec);
        if (issues.isEmpty()) {
            return "Spec is valid.";
        }

        List<String> gaps = SpecValidator.findGaps(spec);
        var sb = new StringBuilder();
        if (!issues.isEmpty()) {
            sb.append("Validation issues:\n");
            issues.forEach(i -> sb.append("  - ").append(i).append("\n"));
        }
        if (!gaps.isEmpty()) {
            sb.append("Fillable gaps (can be resolved with --fill or interactive mode):\n");
            gaps.forEach(g -> sb.append("  - ").append(g).append("\n"));
        }
        return sb.toString().stripTrailing();
    }

    @ShellMethod(value = "Parse an OpenAPI spec and generate a spec JSON template", key = "from-openapi")
    public String fromOpenapi(
            @ShellOption("--url") String url,
            @ShellOption(value = "--output", defaultValue = ShellOption.NULL) String output) throws IOException {

        String openapiJson = fetchOrRead(url);
        var result = OpenApiParser.parse(openapiJson);

        var spec = new ProjectSpec(
                deriveNameFromTitle(result.title()),
                ProjectMode.STANDALONE,
                null,
                "com.example",
                null,
                "both",
                new ProjectSpec.ApiSpec(result.title(), result.baseUrl(), url),
                result.auth(),
                result.endpoints()
        );

        String specJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(spec);

        if (output != null) {
            Files.writeString(Path.of(output), specJson);
            return "Spec template written to %s (%d endpoints extracted)".formatted(output, result.endpoints().size());
        }

        return specJson;
    }

    @ShellMethod(value = "Interactive LLM-driven project generation (requires AI model)", key = "interactive")
    public String interactive() {
        return "Interactive mode requires an AI model. Start with -Pstandalone and configure spring.ai.anthropic.api-key.";
    }

    // --- Helpers ---

    private String fetchOrRead(String urlOrPath) throws IOException {
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlOrPath))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 400) {
                    throw new IOException("HTTP %d fetching %s".formatted(response.statusCode(), urlOrPath));
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted fetching " + urlOrPath, e);
            }
        }
        return Files.readString(Path.of(urlOrPath));
    }

    private ProjectSpec mergeOpenApiResult(ProjectSpec spec, OpenApiParser.ParseResult result) {
        return new ProjectSpec(
                spec.name(),
                spec.mode(),
                spec.outputDir(),
                spec.groupId(),
                spec.packageName(),
                spec.outputFormat(),
                new ProjectSpec.ApiSpec(
                        spec.api().title() != null ? spec.api().title() : result.title(),
                        spec.api().baseUrl() != null ? spec.api().baseUrl() : result.baseUrl(),
                        spec.api().openapiSpec()),
                spec.auth() != null && !"none".equals(spec.auth().type()) ? spec.auth() : result.auth(),
                spec.endpoints() != null && !spec.endpoints().isEmpty() ? spec.endpoints() : result.endpoints()
        );
    }

    private String deriveNameFromTitle(String title) {
        if (title == null || title.isBlank()) return "api";
        return title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .replace("api", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
