package io.jaiclaw.skill.creator;

import java.util.List;
import java.util.Map;

/**
 * Parsed representation of a YAML skill spec for non-interactive generation.
 */
public record SkillSpec(
        String name,
        String description,
        List<String> platforms,
        List<String> requiredBins,
        String purpose
) {

    @SuppressWarnings("unchecked")
    public static SkillSpec fromYamlMap(Map<String, Object> map) {
        String name = (String) map.get("name");
        String description = (String) map.get("description");
        String purpose = (String) map.get("purpose");

        List<String> platforms = map.containsKey("platforms")
                ? toStringList(map.get("platforms"))
                : List.of();
        List<String> requiredBins = map.containsKey("requiredBins")
                ? toStringList(map.get("requiredBins"))
                : List.of();

        return new SkillSpec(name, description, platforms, requiredBins, purpose);
    }

    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Spec is missing required field: name");
        }
        if (!name.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            throw new IllegalArgumentException(
                    "Invalid skill name '%s': must be lowercase letters, digits, and hyphens only".formatted(name));
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Spec is missing required field: description");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("Spec is missing required field: purpose");
        }
    }

    public String toPrompt() {
        var sb = new StringBuilder();
        sb.append("Create a JaiClaw skill with the following specification:\n\n");
        sb.append("Name: ").append(name).append('\n');
        sb.append("Description: ").append(description).append('\n');
        if (platforms != null && !platforms.isEmpty()) {
            sb.append("Platforms: ").append(String.join(", ", platforms)).append('\n');
        }
        if (requiredBins != null && !requiredBins.isEmpty()) {
            sb.append("Required binaries: ").append(String.join(", ", requiredBins)).append('\n');
        }
        sb.append("\nPurpose:\n").append(purpose).append('\n');
        sb.append("\nGenerate the complete SKILL.md file content with proper YAML frontmatter and body instructions.");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of(value.toString());
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String description;
        private List<String> platforms;
        private List<String> requiredBins;
        private String purpose;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder platforms(List<String> platforms) { this.platforms = platforms; return this; }
        public Builder requiredBins(List<String> requiredBins) { this.requiredBins = requiredBins; return this; }
        public Builder purpose(String purpose) { this.purpose = purpose; return this; }

        public SkillSpec build() {
            return new SkillSpec(name, description, platforms, requiredBins, purpose);
        }
    }
}
