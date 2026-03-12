package io.jclaw.skills;

import io.jclaw.core.skill.SkillDefinition;
import io.jclaw.core.skill.SkillMetadata;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Parses SKILL.md files with YAML-like frontmatter into SkillDefinition records.
 *
 * Expected format:
 * <pre>
 * ---
 * name: skill-name
 * description: What the skill does
 * alwaysInclude: false
 * requiredBins: [git, node]
 * platforms: [darwin, linux]
 * ---
 * # Skill content in markdown
 * </pre>
 */
public class SkillMarkdownParser {

    private static final Pattern FRONTMATTER_PATTERN =
            Pattern.compile("^---\\s*\\n(.*?)\\n?---\\s*\\n?", Pattern.DOTALL);

    public SkillDefinition parse(String filename, String rawContent) {
        var matcher = FRONTMATTER_PATTERN.matcher(rawContent);

        Map<String, String> frontmatter;
        String content;

        if (matcher.find()) {
            frontmatter = parseFrontmatter(matcher.group(1));
            content = rawContent.substring(matcher.end()).trim();
        } else {
            frontmatter = Map.of();
            content = rawContent.trim();
        }

        String name = frontmatter.getOrDefault("name",
                filename.replace("SKILL.md", "").replace(".md", "").replaceAll("[^a-zA-Z0-9-]", ""));
        String description = frontmatter.getOrDefault("description", "");
        boolean alwaysInclude = Boolean.parseBoolean(frontmatter.getOrDefault("alwaysInclude", "false"));
        String primaryEnv = frontmatter.getOrDefault("primaryEnv", "");
        Set<String> requiredBins = parseSet(frontmatter.getOrDefault("requiredBins", ""));
        Set<String> platforms = parseSet(frontmatter.getOrDefault("platforms", ""));

        String version = frontmatter.getOrDefault("version", "1.0.0");
        Set<String> tenantIds = parseSet(frontmatter.getOrDefault("tenantIds", ""));

        var metadata = new SkillMetadata(alwaysInclude, primaryEnv, requiredBins, platforms, version, tenantIds);
        return new SkillDefinition(name, description, content, metadata);
    }

    private Map<String, String> parseFrontmatter(String block) {
        var map = new LinkedHashMap<String, String>();
        for (String line : block.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                map.put(key, value);
            }
        }
        return map;
    }

    private Set<String> parseSet(String value) {
        if (value.isBlank()) return Set.of();
        // Handle [a, b, c] format
        String cleaned = value.replaceAll("[\\[\\]]", "").trim();
        if (cleaned.isEmpty()) return Set.of();
        return Set.copyOf(Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }
}
