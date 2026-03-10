package io.jclaw.skills;

import io.jclaw.core.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads skill definitions from bundled resources and workspace directories.
 * Skills are markdown files (SKILL.md) with optional YAML frontmatter.
 */
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    private static final String SKILLS_RESOURCE_PATH = "/skills/";
    private static final String SKILL_FILE_SUFFIX = "SKILL.md";

    private final SkillMarkdownParser parser = new SkillMarkdownParser();
    private final SkillEligibilityChecker eligibilityChecker;

    public SkillLoader() {
        this(new SkillEligibilityChecker());
    }

    public SkillLoader(SkillEligibilityChecker eligibilityChecker) {
        this.eligibilityChecker = eligibilityChecker;
    }

    /**
     * Load bundled skills from classpath resources under /skills/.
     */
    public List<SkillDefinition> loadBundled() {
        var skills = new ArrayList<SkillDefinition>();
        try {
            var resource = getClass().getResource(SKILLS_RESOURCE_PATH);
            if (resource == null) {
                log.debug("No bundled skills directory found on classpath");
                return skills;
            }

            var uri = resource.toURI();
            Path skillsPath;
            if (uri.getScheme().equals("jar")) {
                // Running from JAR — use filesystem provider
                var fs = FileSystems.newFileSystem(uri, java.util.Map.of());
                skillsPath = fs.getPath(SKILLS_RESOURCE_PATH);
            } else {
                skillsPath = Paths.get(uri);
            }

            try (Stream<Path> paths = Files.walk(skillsPath, 2)) {
                paths.filter(p -> p.getFileName().toString().endsWith(SKILL_FILE_SUFFIX))
                        .forEach(p -> loadSkillFile(p, skills));
            }
        } catch (Exception e) {
            log.warn("Failed to load bundled skills", e);
        }
        return filterEligible(skills);
    }

    /**
     * Load skills from an external directory (e.g., workspace .jclaw/skills/).
     */
    public List<SkillDefinition> loadFromDirectory(Path skillsDir) {
        var skills = new ArrayList<SkillDefinition>();
        if (!Files.isDirectory(skillsDir)) {
            log.debug("Skills directory does not exist: {}", skillsDir);
            return skills;
        }

        try (Stream<Path> paths = Files.walk(skillsDir, 3)) {
            paths.filter(p -> p.getFileName().toString().endsWith(SKILL_FILE_SUFFIX))
                    .forEach(p -> loadSkillFile(p, skills));
        } catch (IOException e) {
            log.warn("Failed to load skills from {}", skillsDir, e);
        }
        return filterEligible(skills);
    }

    /**
     * Load all skills from both bundled and workspace directories.
     */
    public List<SkillDefinition> loadAll(Path workspaceSkillsDir) {
        var all = new ArrayList<SkillDefinition>();
        all.addAll(loadBundled());
        if (workspaceSkillsDir != null) {
            all.addAll(loadFromDirectory(workspaceSkillsDir));
        }
        log.info("Loaded {} skills total", all.size());
        return List.copyOf(all);
    }

    private void loadSkillFile(Path path, List<SkillDefinition> target) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String filename = path.getFileName().toString();
            var skill = parser.parse(filename, content);
            target.add(skill);
            log.debug("Loaded skill: {}", skill.name());
        } catch (Exception e) {
            log.warn("Failed to parse skill file: {}", path, e);
        }
    }

    private List<SkillDefinition> filterEligible(List<SkillDefinition> skills) {
        return skills.stream()
                .filter(eligibilityChecker::isEligible)
                .toList();
    }
}
