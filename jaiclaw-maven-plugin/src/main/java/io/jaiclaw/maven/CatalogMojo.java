package io.jaiclaw.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walk a JaiClaw project's skills tree and (optionally) blueprint YAML tree
 * and emit two JSON catalogs to {@code docs/api/}:
 * <ul>
 *   <li>{@code skills-catalog.json} — one entry per SKILL.md, extracted from
 *       the frontmatter</li>
 *   <li>{@code blueprints-catalog.json} — one entry per blueprint YAML file</li>
 * </ul>
 *
 * <p>The JSON files are the docs-side source of truth for skill and
 * blueprint listings — a static-site generator or a plain markdown
 * template can iterate them at build time.
 *
 * <p>Configuration mirrors the Hermes pattern
 * (Python {@code extract-*} scripts + a React catalog component):
 * define once in code, render everywhere. Here, the "define once" is the
 * SKILL.md / blueprint YAML on disk; the JSON is the wire format for docs.
 *
 * <p>Both source trees are optional — the goal skips a catalog silently if
 * its input directory is absent, so a project without blueprints (or without
 * skills) can still run the goal.
 */
@Mojo(name = "catalog", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public class CatalogMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", readonly = true)
    private File baseDir;

    /**
     * Directory to scan for SKILL.md files (recursively). Defaults to the
     * canonical resources path under jaiclaw-skills.
     */
    @Parameter(property = "jaiclaw.catalog.skillsDir",
            defaultValue = "${project.basedir}/core/jaiclaw-skills/src/main/resources/skills")
    private File skillsDir;

    /**
     * Directory to scan for blueprint YAML files. Defaults to the canonical
     * shipped-samples path.
     */
    @Parameter(property = "jaiclaw.catalog.blueprintsDir",
            defaultValue = "${project.basedir}/extensions/jaiclaw-blueprints/src/main/resources/blueprints/samples")
    private File blueprintsDir;

    /**
     * Where to write the generated JSON files.
     */
    @Parameter(property = "jaiclaw.catalog.outputDir",
            defaultValue = "${project.basedir}/docs/api")
    private File outputDir;

    /**
     * Skip the goal entirely.
     */
    @Parameter(property = "jaiclaw.catalog.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("JaiClaw catalog generation skipped");
            return;
        }
        try {
            Files.createDirectories(outputDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Cannot create output dir " + outputDir, e);
        }
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        int skills = writeCatalog("skills-catalog.json",
                collectSkills(skillsDir.toPath()), mapper);
        int blueprints = writeCatalog("blueprints-catalog.json",
                collectBlueprints(blueprintsDir.toPath()), mapper);

        getLog().info("Wrote " + skills + " skill(s) + " + blueprints
                + " blueprint(s) to " + outputDir);
    }

    private int writeCatalog(String filename, List<Map<String, Object>> entries,
                              ObjectMapper mapper) throws MojoExecutionException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("count", entries.size());
        root.put("entries", entries);
        Path out = outputDir.toPath().resolve(filename);
        try {
            mapper.writeValue(out.toFile(), root);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + out, e);
        }
        return entries.size();
    }

    // ── skills ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> collectSkills(Path root) throws MojoExecutionException {
        if (!Files.isDirectory(root)) {
            getLog().info("Skills dir not present, skipping skills catalog: " + root);
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            var files = stream
                    .filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .toList();
            for (Path skill : files) {
                Map<String, Object> parsed = parseSkillFrontmatter(skill);
                if (parsed != null) entries.add(parsed);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to walk " + root, e);
        }
        return entries;
    }

    /**
     * Extract YAML frontmatter (between {@code ---} lines) at the top of a
     * SKILL.md file and return it as a map, augmented with a "path" field
     * that's relative to the project root.
     */
    private Map<String, Object> parseSkillFrontmatter(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
                return null;
            }
            StringBuilder yaml = new StringBuilder();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if ("---".equals(line.trim())) break;
                yaml.append(line).append('\n');
            }
            Object parsed = new Yaml().load(yaml.toString());
            if (!(parsed instanceof Map<?, ?> map)) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            out.put("path", baseDir.toPath().relativize(file).toString());
            return out;
        } catch (IOException | RuntimeException e) {
            getLog().warn("Skipping unparseable SKILL.md " + file + ": " + e.getMessage());
            return null;
        }
    }

    // ── blueprints ───────────────────────────────────────────────────────────

    private List<Map<String, Object>> collectBlueprints(Path root) throws MojoExecutionException {
        if (!Files.isDirectory(root)) {
            getLog().info("Blueprints dir not present, skipping blueprints catalog: " + root);
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            var files = stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();
            for (Path bp : files) {
                Map<String, Object> parsed = parseBlueprint(bp);
                if (parsed != null) entries.add(parsed);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to walk " + root, e);
        }
        return entries;
    }

    private Map<String, Object> parseBlueprint(Path file) {
        try (var in = Files.newInputStream(file)) {
            Object parsed = new Yaml().load(in);
            if (!(parsed instanceof Map<?, ?> map)) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            out.put("path", baseDir.toPath().relativize(file).toString());
            return out;
        } catch (IOException | RuntimeException e) {
            getLog().warn("Skipping unparseable blueprint " + file + ": " + e.getMessage());
            return null;
        }
    }
}
