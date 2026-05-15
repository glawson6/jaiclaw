package io.jaiclaw.scaffold;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@ShellComponent
public class ScaffoldCommands {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldCommands.class);

    private final ProjectGenerator generator = new ProjectGenerator();

    @ShellMethod(value = "Generate a standalone JaiClaw project from a YAML manifest",
            key = {"scaffold create", "scaffold-create"})
    public String create(
            @ShellOption(value = "--manifest", help = "Path to YAML manifest file") String manifestPath,
            @ShellOption(value = "--output-dir", defaultValue = ".",
                    help = "Parent directory for the generated project") String outputDir) {

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
