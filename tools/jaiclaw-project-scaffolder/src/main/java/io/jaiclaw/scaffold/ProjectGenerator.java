package io.jaiclaw.scaffold;

import io.jaiclaw.scaffold.generators.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator — creates directories, calls all generators, logs summary.
 */
public class ProjectGenerator {

    private static final Logger log = LoggerFactory.getLogger(ProjectGenerator.class);

    /**
     * Generate a complete standalone Maven project from a manifest.
     *
     * @param manifest the parsed and validated project manifest
     * @param outputDir parent directory where the project directory will be created
     * @return the path to the generated project root
     */
    public Path generate(ProjectManifest manifest, Path outputDir) throws IOException {
        Path projectDir = outputDir.resolve(manifest.artifactId());
        List<String> generatedFiles = new ArrayList<>();

        log.info("Generating project '{}' in {}", manifest.name(), projectDir);

        // Create directory structure
        Path srcMain = projectDir.resolve("src/main/java/" + manifest.packagePath());
        Path srcResources = projectDir.resolve("src/main/resources");
        Path srcTest = projectDir.resolve("src/test/groovy/" + manifest.packagePath());
        Files.createDirectories(srcMain);
        Files.createDirectories(srcResources);
        Files.createDirectories(srcTest);

        // pom.xml
        writeFile(projectDir.resolve("pom.xml"), PomGenerator.generate(manifest));
        generatedFiles.add("pom.xml");

        // application.yml
        writeFile(srcResources.resolve("application.yml"), ApplicationYmlGenerator.generate(manifest));
        generatedFiles.add("src/main/resources/application.yml");

        // Main application class
        String appClassName = manifest.applicationClassName() + ".java";
        writeFile(srcMain.resolve(appClassName), ApplicationClassGenerator.generate(manifest));
        generatedFiles.add("src/main/java/.../" + appClassName);

        // Custom tool stubs
        for (var tool : manifest.customTools()) {
            String toolClassName = ToolStubGenerator.toClassName(tool.name()) + ".java";
            writeFile(srcMain.resolve(toolClassName), ToolStubGenerator.generate(manifest, tool));
            generatedFiles.add("src/main/java/.../" + toolClassName);
        }

        // System prompt (if classpath strategy)
        if (manifest.agent().systemPrompt() != null
                && "classpath".equals(manifest.agent().systemPrompt().strategy())
                && manifest.agent().systemPrompt().source() != null) {
            Path promptPath = srcResources.resolve(manifest.agent().systemPrompt().source());
            Files.createDirectories(promptPath.getParent());
            writeFile(promptPath, SystemPromptGenerator.generate(manifest));
            generatedFiles.add("src/main/resources/" + manifest.agent().systemPrompt().source());
        }

        // README.md
        writeFile(projectDir.resolve("README.md"), ReadmeGenerator.generate(manifest));
        generatedFiles.add("README.md");

        // Log summary
        log.info("Generated {} files:", generatedFiles.size());
        for (String f : generatedFiles) {
            log.info("  {}", f);
        }

        return projectDir;
    }

    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
