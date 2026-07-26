package io.jaiclaw.scaffold;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;
import org.springframework.shell.core.command.annotation.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Component
public class ScaffoldCommands {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldCommands.class);

    private final ProjectGenerator generator = new ProjectGenerator();

    @Command(name = "scaffold create", alias = "scaffold-create", description = "Generate a standalone JaiClaw project from a YAML manifest")
    public String create(
            @Option(longName = "manifest", description = "Path to YAML manifest file") String manifestPath,
            @Option(longName = "output-dir", defaultValue = ".", description = "Parent directory for the generated project") String outputDir) {

        Path manifestFile = Path.of(manifestPath);
        if (!Files.exists(manifestFile)) {
            return "Manifest file not found: " + manifestPath;
        }

        try {
            // Parse YAML
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = yamlMapper.readValue(manifestFile.toFile(), Map.class);

            ProjectManifest manifest = ProjectManifest.fromYamlMap(yamlMap);
            manifest.validate();

            // Generate
            Path projectPath = generator.generate(manifest, Path.of(outputDir));

            return "Project generated at: " + projectPath.toAbsolutePath();
        } catch (IllegalArgumentException e) {
            return "Invalid manifest: " + e.getMessage();
        } catch (IOException e) {
            return "Failed to generate project: " + e.getMessage();
        }
    }
}
