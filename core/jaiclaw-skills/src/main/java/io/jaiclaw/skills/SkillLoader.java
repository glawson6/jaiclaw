package io.jaiclaw.skills;

import io.jaiclaw.core.skill.SkillDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads skill definitions from bundled resources and workspace directories.
 * Skills are markdown files (SKILL.md) with optional YAML frontmatter.
 */
public class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);
    private static final String SKILLS_RESOURCE_PATH = "/skills/";
    private static final String SKILL_FILE_SUFFIX = "SKILL.md";
    /** Sidecar written beside a learned skill by {@code jaiclaw-learning}. */
    private static final String LEARNED_SIDECAR_FILE = ".jaiclaw-learning.json";

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
                // Running from JAR — get existing filesystem (opened by Spring Boot's
                // launcher) or create a new one if not yet opened.
                FileSystem fs;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    fs = FileSystems.newFileSystem(uri, java.util.Map.of());
                }
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
     * Load skills from an external directory (e.g., workspace .jaiclaw/skills/).
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
     * Loads agent-authored skills written by {@code jaiclaw-learning}, from
     * {@code {learnedDir}/{tenantId}/{skillName}/SKILL.md}.
     *
     * <p>Skills whose sidecar marks them {@code ARCHIVED} are <strong>skipped</strong>.
     * The curator archives a skill by editing its sidecar rather than deleting the
     * directory — a skill the agent wrote records what it learned, and erasing that
     * to save disk would be destroying evidence. Honouring the lifecycle here is
     * what makes archiving take effect.
     *
     * <p>Lifecycle is read from the sidecar with a tiny hand-rolled scan rather than
     * a JSON dependency: {@code jaiclaw-skills} is on every adopter's classpath and
     * this reads exactly one field. An unreadable or absent sidecar is treated as
     * ACTIVE — a skill on disk should load unless something positively says not to.
     *
     * @param learnedDir base directory learned skills live under; null or missing
     *                   yields an empty list
     * @param tenantId   tenant whose skills to load; null means {@code default}
     */
    public List<SkillDefinition> loadLearned(Path learnedDir, String tenantId) {
        if (learnedDir == null) return List.of();
        Path tenantDir = learnedDir.resolve(safeTenant(tenantId));
        if (!Files.isDirectory(tenantDir)) {
            log.debug("No learned skills directory for tenant {}: {}", tenantId, tenantDir);
            return List.of();
        }

        var skills = new ArrayList<SkillDefinition>();
        int archived = 0;
        try (Stream<Path> dirs = Files.list(tenantDir)) {
            for (Path skillDir : dirs.filter(Files::isDirectory).toList()) {
                Path skillFile = skillDir.resolve(SKILL_FILE_SUFFIX);
                if (!Files.isRegularFile(skillFile)) continue;
                if (isArchived(skillDir)) {
                    archived++;
                    continue;
                }
                loadSkillFile(skillFile, skills);
            }
        } catch (IOException e) {
            // A broken learned-skills directory must not stop the agent starting.
            log.warn("Failed to load learned skills from {}", tenantDir, e);
            return List.of();
        }

        List<SkillDefinition> eligible = filterEligible(skills);
        if (!eligible.isEmpty() || archived > 0) {
            log.info("Loaded {} learned skill(s) for tenant {} ({} archived, skipped)",
                    eligible.size(), tenantId == null ? "default" : tenantId, archived);
        }
        return eligible;
    }

    /**
     * Whether a learned skill's sidecar marks it ARCHIVED.
     *
     * <p>Fails open: any problem reading the sidecar yields {@code false}, so a
     * skill loads unless the sidecar positively says it is archived.
     */
    private boolean isArchived(Path skillDir) {
        Path sidecar = skillDir.resolve(LEARNED_SIDECAR_FILE);
        if (!Files.isRegularFile(sidecar)) return false;
        try {
            String json = Files.readString(sidecar, java.nio.charset.StandardCharsets.UTF_8);
            int k = json.indexOf("\"lifecycle\"");
            if (k < 0) return false;
            int colon = json.indexOf(':', k);
            if (colon < 0) return false;
            int open = json.indexOf('"', colon);
            if (open < 0) return false;
            int close = json.indexOf('"', open + 1);
            if (close < 0) return false;
            return "ARCHIVED".equalsIgnoreCase(json.substring(open + 1, close).trim());
        } catch (IOException | RuntimeException e) {
            log.debug("Unreadable learned-skill sidecar at {} — treating as active", sidecar);
            return false;
        }
    }

    /** Mirrors the sanitisation the learning module applies when writing. */
    private static String safeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return "default";
        String cleaned = tenantId.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        if (cleaned.equals(".") || cleaned.equals("..")) return "_" + cleaned;
        return cleaned.length() > 128 ? cleaned.substring(0, 128) : cleaned;
    }

    /**
     * Load skills according to configuration: filtered bundled skills merged with
     * workspace skills, where workspace skills override same-name bundled ones.
     *
     * @param allowBundled which bundled skills to include: ["*"] for all, [] for none,
     *                     or specific names like ["coding", "github"]
     * @param workspaceDir external directory for custom/override skills, or null to skip
     * @return merged skill list with workspace overrides applied
     */
    public List<SkillDefinition> loadConfigured(List<String> allowBundled, String workspaceDir) {
        List<SkillDefinition> bundled = loadBundledFiltered(allowBundled);

        List<SkillDefinition> workspace = workspaceDir != null
                ? loadFromDirectory(Path.of(workspaceDir))
                : List.of();

        // Merge: workspace overrides bundled by name
        var merged = new LinkedHashMap<String, SkillDefinition>();
        bundled.forEach(s -> merged.put(s.name(), s));
        workspace.forEach(s -> merged.put(s.name(), s));

        var result = List.copyOf(merged.values());
        log.info("Loaded {} skills ({} bundled, {} workspace, {} after merge)",
                result.size(), bundled.size(), workspace.size(), result.size());
        return result;
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

    private List<SkillDefinition> loadBundledFiltered(List<String> allowBundled) {
        if (allowBundled == null || allowBundled.contains("*")) {
            return loadBundled();
        }
        if (allowBundled.isEmpty()) {
            return List.of();
        }
        var allowed = Set.copyOf(allowBundled);
        return loadBundled().stream()
                .filter(s -> allowed.contains(s.name()))
                .toList();
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
