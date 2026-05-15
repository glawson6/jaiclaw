package io.jaiclaw.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.jaiclaw.scaffold.ProjectGenerator;
import io.jaiclaw.scaffold.ProjectManifest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

/**
 * Generates a standalone JaiClaw Maven project from a YAML manifest.
 *
 * <p>Usage:
 * <pre>
 * mvn io.jaiclaw:jaiclaw-maven-plugin:scaffold \
 *     -Djaiclaw.scaffold.manifest=my-bot.yml \
 *     -Djaiclaw.scaffold.outputDir=./projects
 * </pre>
 */
@Mojo(name = "scaffold", requiresProject = false)
public class ScaffoldMojo extends AbstractMojo {

    /**
     * Path to the YAML manifest file.
     */
    @Parameter(property = "jaiclaw.scaffold.manifest", required = true)
    private File manifest;

    /**
     * Parent directory where the generated project will be created.
     */
    @Parameter(property = "jaiclaw.scaffold.outputDir", defaultValue = ".")
    private File outputDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!manifest.isFile()) {
            throw new MojoFailureException("Manifest file not found: " + manifest.getAbsolutePath());
        }

        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = yamlMapper.readValue(manifest, Map.class);

            ProjectManifest projectManifest = ProjectManifest.fromYamlMap(yamlMap);
            projectManifest.validate();

            ProjectGenerator generator = new ProjectGenerator();
            Path projectPath = generator.generate(projectManifest, outputDir.toPath());

            getLog().info("Project generated at: " + projectPath.toAbsolutePath());
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException("Invalid manifest: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate project: " + e.getMessage(), e);
        }
    }
}
