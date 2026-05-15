package io.jaiclaw.scaffold;

import java.util.List;
import java.util.Map;

/**
 * Parsed representation of a jaiclaw-manifest.yml file.
 * Only {@code name} and {@code description} are required; all other fields have sensible defaults.
 */
public record ProjectManifest(
        String name,
        String description,
        String groupId,
        String javaPackage,
        String version,
        String jaiclawVersion,
        ParentMode parentMode,
        Archetype archetype,
        AiProvider aiProvider,
        List<String> extensions,
        List<String> channels,
        AgentConfig agent,
        SkillsConfig skills,
        SecurityConfig security,
        List<CustomTool> customTools,
        CamelConfig camel,
        EmbabelConfig embabel,
        ServerConfig server,
        DockerConfig docker,
        ReadmeConfig readme
) {

    /**
     * Controls the Maven parent POM strategy for the generated project.
     *
     * <ul>
     *   <li>{@code STANDALONE} — uses {@code spring-boot-starter-parent} + {@code jaiclaw-bom} import.
     *       Fully self-contained; can live anywhere on disk without the JaiClaw monorepo.</li>
     *   <li>{@code JAICLAW} — uses {@code jaiclaw-parent} as Maven parent.
     *       Inherits all managed versions, plugin config, and test infrastructure.
     *       Requires JaiClaw parent POM to be resolvable (published to repo or locally installed).</li>
     * </ul>
     */
    public enum ParentMode {
        STANDALONE, JAICLAW;

        public static ParentMode fromString(String s) {
            if (s == null) return STANDALONE;
            return valueOf(s.toUpperCase().replace("-", "_"));
        }
    }

    public enum Archetype {
        GATEWAY, EMBABEL, CAMEL, COMPREHENSIVE, MINIMAL;

        public static Archetype fromString(String s) {
            if (s == null) return GATEWAY;
            return valueOf(s.toUpperCase().replace("-", "_"));
        }
    }

    public record AiProvider(String primary, List<String> additional) {}
    public record AgentConfig(String name, String toolsProfile, SystemPromptConfig systemPrompt) {}
    public record SystemPromptConfig(String strategy, String content, String source) {}
    public record SkillsConfig(List<String> allowBundled) {}
    public record SecurityConfig(String mode) {}
    public record CustomTool(String name, String description, String section, Map<String, ToolParameter> parameters) {}
    public record ToolParameter(String type, String description, boolean required) {}
    public record CamelConfig(String channelId, String displayName, boolean stateless, String version) {}
    public record EmbabelConfig(String defaultLlm, String workflow) {}
    public record ServerConfig(int port) {}
    public record DockerConfig(boolean enabled, String baseImage) {}
    public record ReadmeConfig(String problem, String solution) {}

    // --- Derived helpers ---

    public String artifactId() {
        return name;
    }

    public String applicationClassName() {
        return toPascalCase(name) + "Application";
    }

    public String packagePath() {
        return javaPackage.replace('.', '/');
    }

    // --- YAML parsing ---

    @SuppressWarnings("unchecked")
    public static ProjectManifest fromYamlMap(Map<String, Object> map) {
        String name = requireString(map, "name");
        String description = requireString(map, "description");
        String groupId = stringOrDefault(map, "group-id", "com.example");
        String javaPackage = stringOrDefault(map, "java-package", null);
        String version = stringOrDefault(map, "version", "0.1.0-SNAPSHOT");
        String jaiclawVersion = stringOrDefault(map, "jaiclaw-version", "0.3.0-SNAPSHOT");
        ParentMode parentMode = ParentMode.fromString(stringOrDefault(map, "parent", "standalone"));
        Archetype archetype = Archetype.fromString(stringOrDefault(map, "archetype", "gateway"));

        // AI Provider
        AiProvider aiProvider;
        if (map.containsKey("ai-provider")) {
            Map<String, Object> aiMap = (Map<String, Object>) map.get("ai-provider");
            String primary = stringOrDefault(aiMap, "primary", "anthropic");
            List<String> additional = toStringList(aiMap.get("additional"));
            aiProvider = new AiProvider(primary, additional);
        } else {
            aiProvider = new AiProvider("anthropic", List.of());
        }

        List<String> extensions = toStringList(map.get("extensions"));
        List<String> channels = toStringList(map.get("channels"));

        // Agent
        AgentConfig agent;
        if (map.containsKey("agent")) {
            Map<String, Object> agentMap = (Map<String, Object>) map.get("agent");
            String agentName = stringOrDefault(agentMap, "name", toPascalCase(name) + " Agent");
            String toolsProfile = stringOrDefault(agentMap, "tools-profile", "full");
            SystemPromptConfig systemPrompt = parseSystemPrompt(agentMap);
            agent = new AgentConfig(agentName, toolsProfile, systemPrompt);
        } else {
            agent = new AgentConfig(toPascalCase(name) + " Agent", "full",
                    new SystemPromptConfig("none", null, null));
        }

        // Skills
        SkillsConfig skills;
        if (map.containsKey("skills")) {
            Map<String, Object> skillsMap = (Map<String, Object>) map.get("skills");
            List<String> allowBundled = toStringList(skillsMap.get("allow-bundled"));
            skills = new SkillsConfig(allowBundled);
        } else {
            skills = new SkillsConfig(List.of());
        }

        // Security
        SecurityConfig security;
        if (map.containsKey("security")) {
            Map<String, Object> secMap = (Map<String, Object>) map.get("security");
            security = new SecurityConfig(stringOrDefault(secMap, "mode", "api-key"));
        } else {
            security = new SecurityConfig("api-key");
        }

        // Custom tools
        List<CustomTool> customTools = List.of();
        if (map.containsKey("custom-tools")) {
            List<Map<String, Object>> toolsList = (List<Map<String, Object>>) map.get("custom-tools");
            customTools = toolsList.stream().map(ProjectManifest::parseCustomTool).toList();
        }

        // Camel
        CamelConfig camel = null;
        if (map.containsKey("camel")) {
            Map<String, Object> camelMap = (Map<String, Object>) map.get("camel");
            camel = new CamelConfig(
                    stringOrDefault(camelMap, "channel-id", name),
                    stringOrDefault(camelMap, "display-name", toPascalCase(name)),
                    boolOrDefault(camelMap, "stateless", true),
                    stringOrDefault(camelMap, "version", "4.18.1")
            );
        }

        // Embabel
        EmbabelConfig embabel = null;
        if (map.containsKey("embabel")) {
            Map<String, Object> embabelMap = (Map<String, Object>) map.get("embabel");
            embabel = new EmbabelConfig(
                    stringOrDefault(embabelMap, "default-llm", "claude-sonnet-4-5"),
                    stringOrDefault(embabelMap, "workflow", null)
            );
        }

        // Server
        ServerConfig server;
        if (map.containsKey("server")) {
            Map<String, Object> serverMap = (Map<String, Object>) map.get("server");
            server = new ServerConfig(intOrDefault(serverMap, "port", 8080));
        } else {
            server = new ServerConfig(8080);
        }

        // Docker
        DockerConfig docker;
        if (map.containsKey("docker")) {
            Map<String, Object> dockerMap = (Map<String, Object>) map.get("docker");
            docker = new DockerConfig(
                    boolOrDefault(dockerMap, "enabled", true),
                    stringOrDefault(dockerMap, "base-image", "eclipse-temurin:21-jre")
            );
        } else {
            docker = new DockerConfig(true, "eclipse-temurin:21-jre");
        }

        // Readme
        ReadmeConfig readme;
        if (map.containsKey("readme")) {
            Map<String, Object> readmeMap = (Map<String, Object>) map.get("readme");
            readme = new ReadmeConfig(
                    stringOrDefault(readmeMap, "problem", ""),
                    stringOrDefault(readmeMap, "solution", "")
            );
        } else {
            readme = new ReadmeConfig("", "");
        }

        // Derive java package if not provided
        if (javaPackage == null || javaPackage.isBlank()) {
            javaPackage = groupId + "." + name.replace("-", "");
        }

        return new ProjectManifest(name, description, groupId, javaPackage, version,
                jaiclawVersion, parentMode, archetype, aiProvider, extensions, channels, agent,
                skills, security, customTools, camel, embabel, server, docker, readme);
    }

    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Manifest is missing required field: name");
        }
        if (!name.matches("^[a-z0-9]+(-[a-z0-9]+)*$")) {
            throw new IllegalArgumentException(
                    "Invalid project name '%s': must be kebab-case (lowercase letters, digits, and hyphens)".formatted(name));
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Manifest is missing required field: description");
        }
        for (String ext : extensions) {
            if (!KnownModules.EXTENSIONS.contains(ext)) {
                throw new IllegalArgumentException("Unknown extension: " + ext
                        + ". Valid extensions: " + KnownModules.EXTENSIONS);
            }
        }
        for (String ch : channels) {
            if (!KnownModules.CHANNELS.contains(ch)) {
                throw new IllegalArgumentException("Unknown channel: " + ch
                        + ". Valid channels: " + KnownModules.CHANNELS);
            }
        }
        if (!KnownModules.AI_PROVIDERS.contains(aiProvider.primary())) {
            throw new IllegalArgumentException("Unknown primary AI provider: " + aiProvider.primary()
                    + ". Valid providers: " + KnownModules.AI_PROVIDERS);
        }
        for (String p : aiProvider.additional()) {
            if (!KnownModules.AI_PROVIDERS.contains(p)) {
                throw new IllegalArgumentException("Unknown additional AI provider: " + p
                        + ". Valid providers: " + KnownModules.AI_PROVIDERS);
            }
        }
        if (!KnownModules.TOOL_PROFILES.contains(agent.toolsProfile())) {
            throw new IllegalArgumentException("Unknown tools profile: " + agent.toolsProfile()
                    + ". Valid profiles: " + KnownModules.TOOL_PROFILES);
        }
        if (!KnownModules.SECURITY_MODES.contains(security.mode())) {
            throw new IllegalArgumentException("Unknown security mode: " + security.mode()
                    + ". Valid modes: " + KnownModules.SECURITY_MODES);
        }
        String strategy = agent.systemPrompt() != null ? agent.systemPrompt().strategy() : "none";
        if (!KnownModules.PROMPT_STRATEGIES.contains(strategy)) {
            throw new IllegalArgumentException("Unknown system prompt strategy: " + strategy
                    + ". Valid strategies: " + KnownModules.PROMPT_STRATEGIES);
        }
        if (archetype == Archetype.CAMEL && camel == null) {
            throw new IllegalArgumentException("Archetype 'camel' requires a 'camel' configuration section");
        }
        if (archetype == Archetype.EMBABEL && embabel == null) {
            throw new IllegalArgumentException("Archetype 'embabel' requires an 'embabel' configuration section");
        }
    }

    // --- Builder ---

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String name;
        private String description;
        private String groupId = "com.example";
        private String javaPackage;
        private String version = "0.1.0-SNAPSHOT";
        private String jaiclawVersion = "0.3.0-SNAPSHOT";
        private ParentMode parentMode = ParentMode.STANDALONE;
        private Archetype archetype = Archetype.GATEWAY;
        private AiProvider aiProvider = new AiProvider("anthropic", List.of());
        private List<String> extensions = List.of();
        private List<String> channels = List.of();
        private AgentConfig agent;
        private SkillsConfig skills = new SkillsConfig(List.of());
        private SecurityConfig security = new SecurityConfig("api-key");
        private List<CustomTool> customTools = List.of();
        private CamelConfig camel;
        private EmbabelConfig embabel;
        private ServerConfig server = new ServerConfig(8080);
        private DockerConfig docker = new DockerConfig(true, "eclipse-temurin:21-jre");
        private ReadmeConfig readme = new ReadmeConfig("", "");

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder groupId(String groupId) { this.groupId = groupId; return this; }
        public Builder javaPackage(String javaPackage) { this.javaPackage = javaPackage; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder jaiclawVersion(String jaiclawVersion) { this.jaiclawVersion = jaiclawVersion; return this; }
        public Builder parentMode(ParentMode parentMode) { this.parentMode = parentMode; return this; }
        public Builder archetype(Archetype archetype) { this.archetype = archetype; return this; }
        public Builder aiProvider(AiProvider aiProvider) { this.aiProvider = aiProvider; return this; }
        public Builder extensions(List<String> extensions) { this.extensions = extensions; return this; }
        public Builder channels(List<String> channels) { this.channels = channels; return this; }
        public Builder agent(AgentConfig agent) { this.agent = agent; return this; }
        public Builder skills(SkillsConfig skills) { this.skills = skills; return this; }
        public Builder security(SecurityConfig security) { this.security = security; return this; }
        public Builder customTools(List<CustomTool> customTools) { this.customTools = customTools; return this; }
        public Builder camel(CamelConfig camel) { this.camel = camel; return this; }
        public Builder embabel(EmbabelConfig embabel) { this.embabel = embabel; return this; }
        public Builder server(ServerConfig server) { this.server = server; return this; }
        public Builder docker(DockerConfig docker) { this.docker = docker; return this; }
        public Builder readme(ReadmeConfig readme) { this.readme = readme; return this; }

        public ProjectManifest build() {
            String pkg = javaPackage != null ? javaPackage
                    : (name != null ? groupId + "." + name.replace("-", "") : groupId);
            if (agent == null) {
                String agentName = name != null ? toPascalCase(name) + " Agent" : "Agent";
                agent = new AgentConfig(agentName, "full",
                        new SystemPromptConfig("none", null, null));
            }
            return new ProjectManifest(name, description, groupId, pkg, version,
                    jaiclawVersion, parentMode, archetype, aiProvider, extensions, channels,
                    agent, skills, security, customTools, camel, embabel, server, docker, readme);
        }
    }

    // --- Internal helpers ---

    public static String toPascalCase(String kebab) {
        if (kebab == null) return "";
        var sb = new StringBuilder();
        for (String part : kebab.split("-")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Manifest is missing required field: " + key);
        }
        return value.toString().trim();
    }

    private static String stringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString().trim() : defaultValue;
    }

    private static boolean boolOrDefault(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    private static int intOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static SystemPromptConfig parseSystemPrompt(Map<String, Object> agentMap) {
        if (!agentMap.containsKey("system-prompt")) {
            return new SystemPromptConfig("none", null, null);
        }
        Map<String, Object> spMap = (Map<String, Object>) agentMap.get("system-prompt");
        return new SystemPromptConfig(
                stringOrDefault(spMap, "strategy", "none"),
                stringOrDefault(spMap, "content", null),
                stringOrDefault(spMap, "source", null)
        );
    }

    @SuppressWarnings("unchecked")
    private static CustomTool parseCustomTool(Map<String, Object> toolMap) {
        String name = requireString(toolMap, "name");
        String description = stringOrDefault(toolMap, "description", "");
        String section = stringOrDefault(toolMap, "section", "custom");
        Map<String, ToolParameter> parameters = new java.util.LinkedHashMap<>();
        if (toolMap.containsKey("parameters")) {
            Map<String, Object> paramsMap = (Map<String, Object>) toolMap.get("parameters");
            for (var entry : paramsMap.entrySet()) {
                Map<String, Object> paramDef = (Map<String, Object>) entry.getValue();
                parameters.put(entry.getKey(), new ToolParameter(
                        stringOrDefault(paramDef, "type", "string"),
                        stringOrDefault(paramDef, "description", ""),
                        boolOrDefault(paramDef, "required", false)
                ));
            }
        }
        return new CustomTool(name, description, section, parameters);
    }
}
