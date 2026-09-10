package io.jaiclaw.learning.skill;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.learning.util.PathSegments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads and writes learned skills on disk.
 *
 * <pre>
 *   {skillsDir}/{tenantId}/{skillName}/SKILL.md
 *   {skillsDir}/{tenantId}/{skillName}/.jaiclaw-learning.json
 * </pre>
 *
 * <p>The SKILL.md carries ordinary frontmatter plus {@code x-jaiclaw-learned:
 * true}, so an operator reading the file can tell at a glance that an agent
 * wrote it. All bookkeeping (version, usage, lifecycle) lives in the sidecar
 * rather than the frontmatter, keeping the skill file exactly what a human would
 * have written by hand.
 *
 * <p>Writes are atomic. Skill and tenant names are reduced to safe path segments
 * before use — a skill name reaching here came from a language model.
 *
 * <p>Phase 4B of the 1.2.0 plan.
 */
@Experimental
public class SkillWriter {

    private static final Logger log = LoggerFactory.getLogger(SkillWriter.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    public static final String SKILL_FILE = "SKILL.md";
    public static final String SIDECAR_FILE = ".jaiclaw-learning.json";

    private final Path skillsDir;

    public SkillWriter(Path skillsDir) {
        this.skillsDir = skillsDir;
    }

    /** Directory holding one skill's files. */
    public Path skillDir(String tenantId, String skillName) {
        return skillsDir.resolve(PathSegments.safe(tenantId)).resolve(PathSegments.safe(skillName));
    }

    public boolean exists(String tenantId, String skillName) {
        return Files.exists(skillDir(tenantId, skillName).resolve(SKILL_FILE));
    }

    /**
     * Creates or overwrites a skill.
     *
     * @param body the instructions; frontmatter is generated, not taken from here
     */
    public void write(String tenantId, String skillName, String description,
                      String body, LearnedSkillSidecar sidecar) {
        Path dir = skillDir(tenantId, skillName);
        try {
            Files.createDirectories(dir);
            writeAtomic(dir.resolve(SKILL_FILE), renderSkillMarkdown(
                    PathSegments.safe(skillName), description, body, sidecar, tenantId));
            writeSidecar(tenantId, skillName, sidecar);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write skill " + skillName, e);
        }
    }

    /** The instruction body, with frontmatter stripped. */
    public Optional<String> readBody(String tenantId, String skillName) {
        Path file = skillDir(tenantId, skillName).resolve(SKILL_FILE);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(stripFrontmatter(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            log.warn("Could not read skill {} for tenant {}", skillName, tenantId, e);
            return Optional.empty();
        }
    }

    /** The raw file, frontmatter included. Used by rollback to restore exact bytes. */
    public Optional<String> readRaw(String tenantId, String skillName) {
        Path file = skillDir(tenantId, skillName).resolve(SKILL_FILE);
        if (!Files.exists(file)) return Optional.empty();
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Replaces the file with exact content. Used only by rollback. */
    public void writeRaw(String tenantId, String skillName, String rawContent) {
        Path dir = skillDir(tenantId, skillName);
        try {
            Files.createDirectories(dir);
            writeAtomic(dir.resolve(SKILL_FILE), rawContent);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to restore skill " + skillName, e);
        }
    }

    public Optional<LearnedSkillSidecar> readSidecar(String tenantId, String skillName) {
        Path file = skillDir(tenantId, skillName).resolve(SIDECAR_FILE);
        if (!Files.exists(file)) return Optional.empty();
        try {
            JsonNode n = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            return Optional.of(new LearnedSkillSidecar(
                    text(n, "skillName"), text(n, "tenantId"), text(n, "originSessionKey"),
                    text(n, "proposalId"),
                    n.has("version") ? n.get("version").asInt() : 1,
                    SkillLifecycle.valueOf(textOr(n, "lifecycle", "ACTIVE")),
                    n.has("pinned") && n.get("pinned").asBoolean(),
                    n.has("useCount") ? n.get("useCount").asLong() : 0L,
                    parseInstant(text(n, "createdAt")),
                    parseInstant(text(n, "lastUsedAt"))));
        } catch (Exception e) {
            log.warn("Unreadable sidecar for skill {} (tenant {}) — treating as absent",
                    skillName, tenantId);
            return Optional.empty();
        }
    }

    public void writeSidecar(String tenantId, String skillName, LearnedSkillSidecar sidecar) {
        Path dir = skillDir(tenantId, skillName);
        try {
            Files.createDirectories(dir);
            ObjectNode n = MAPPER.createObjectNode();
            n.put("skillName", sidecar.skillName());
            n.put("tenantId", sidecar.tenantId());
            n.put("originSessionKey", sidecar.originSessionKey());
            n.put("proposalId", sidecar.proposalId());
            n.put("version", sidecar.version());
            n.put("lifecycle", sidecar.lifecycle().name());
            n.put("pinned", sidecar.pinned());
            n.put("useCount", sidecar.useCount());
            n.put("createdAt", sidecar.createdAt().toString());
            n.put("lastUsedAt", sidecar.lastUsedAt() == null ? null : sidecar.lastUsedAt().toString());
            writeAtomic(dir.resolve(SIDECAR_FILE), MAPPER.writeValueAsString(n));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write sidecar for " + skillName, e);
        }
    }

    /** Names of every learned skill for a tenant. */
    public List<String> listSkills(String tenantId) {
        Path dir = skillsDir.resolve(PathSegments.safe(tenantId));
        if (!Files.isDirectory(dir)) return List.of();
        List<String> names = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(dir)) {
            dirs.filter(Files::isDirectory)
                    .filter(d -> Files.exists(d.resolve(SKILL_FILE)))
                    .forEach(d -> names.add(d.getFileName().toString()));
        } catch (IOException e) {
            log.warn("Could not list learned skills for tenant {}", tenantId, e);
            return List.of();
        }
        names.sort(String::compareTo);
        return List.copyOf(names);
    }

    /** Deletes a skill's directory. Used when rolling back a creation. */
    public boolean delete(String tenantId, String skillName) {
        Path dir = skillDir(tenantId, skillName);
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // Best effort — a leftover file is not worth failing a rollback.
                        }
                    });
            return !Files.exists(dir);
        } catch (IOException e) {
            log.warn("Could not delete skill {} for tenant {}", skillName, tenantId, e);
            return false;
        }
    }

    /** Renders frontmatter + body in the shape {@code SkillMarkdownParser} expects. */
    static String renderSkillMarkdown(String skillName, String description, String body,
                                      LearnedSkillSidecar sidecar, String tenantId) {
        return """
                ---
                name: %s
                description: %s
                version: %s
                tenantIds: %s
                alwaysInclude: false
                x-jaiclaw-learned: true
                ---

                %s
                """.formatted(
                skillName,
                description == null ? "" : description.replace("\n", " ").strip(),
                "1.0." + (sidecar == null ? 0 : sidecar.version() - 1),
                tenantId == null ? "" : tenantId,
                body == null ? "" : body.strip());
    }

    /** Removes a leading {@code ---} frontmatter block, if present. */
    static String stripFrontmatter(String content) {
        if (content == null) return "";
        String trimmed = content.stripLeading();
        if (!trimmed.startsWith("---")) return content.strip();
        int end = trimmed.indexOf("\n---", 3);
        if (end < 0) return content.strip();
        int bodyStart = trimmed.indexOf('\n', end + 1);
        return bodyStart < 0 ? "" : trimmed.substring(bodyStart + 1).strip();
    }

    private static void writeAtomic(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static String textOr(JsonNode n, String field, String fallback) {
        String v = text(n, field);
        return v == null ? fallback : v;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
