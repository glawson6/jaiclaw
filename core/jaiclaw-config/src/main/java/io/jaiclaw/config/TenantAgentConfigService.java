package io.jaiclaw.config;

import io.jaiclaw.core.tenant.TenantMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.Yaml;

/**
 * Loads, merges, and caches per-tenant agent configurations.
 *
 * <h3>Config Source Hierarchy</h3>
 * <ol>
 *   <li>{@code application.yml} defaults from {@link AgentProperties.AgentConfig}</li>
 *   <li>Per-tenant YAML files from {@code configLocations} directories</li>
 *   <li>Per-tenant {@code .env} files — variables available for {@code ${VAR}} interpolation</li>
 * </ol>
 *
 * <p><b>SINGLE mode:</b> {@code resolve("default")} returns config from application.yml. No file scanning.
 * <p><b>MULTI mode:</b> Scans {@code configLocations} for {@code {tenantId}.yml} + {@code {tenantId}.env}.
 */
public class TenantAgentConfigService {

    private static final Logger log = LoggerFactory.getLogger(TenantAgentConfigService.class);
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final TenantConfigProperties tenantConfig;
    private final AgentProperties agentProperties;
    private final TenantEnvLoader envLoader;
    private final ResourceLoader resourceLoader;
    private final ConcurrentHashMap<String, TenantAgentConfig> cache = new ConcurrentHashMap<>();

    public TenantAgentConfigService(TenantConfigProperties tenantConfig,
                                    AgentProperties agentProperties,
                                    TenantEnvLoader envLoader,
                                    ResourceLoader resourceLoader) {
        this.tenantConfig = tenantConfig;
        this.agentProperties = agentProperties;
        this.envLoader = envLoader;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Resolve the agent configuration for a given tenant.
     * In SINGLE mode, always returns the default agent config.
     * In MULTI mode, loads from per-tenant YAML + .env files, merging with defaults.
     */
    public TenantAgentConfig resolve(String tenantId) {
        return cache.computeIfAbsent(tenantId, this::loadConfig);
    }

    /**
     * Force reload configuration for a specific tenant.
     */
    public void reload(String tenantId) {
        cache.remove(tenantId);
        cache.computeIfAbsent(tenantId, this::loadConfig);
        log.info("Reloaded tenant config: {}", tenantId);
    }

    /**
     * Return all currently loaded tenant configurations.
     */
    public Map<String, TenantAgentConfig> allConfigurations() {
        return Map.copyOf(cache);
    }

    /**
     * Scan all config locations and pre-load tenant configs (used at startup in MULTI mode).
     */
    public void scanAndLoadAll() {
        if (tenantConfig.mode() != TenantMode.MULTI) {
            log.debug("SINGLE mode — skipping tenant config scan");
            return;
        }

        for (String location : tenantConfig.configLocations()) {
            scanLocation(location);
        }

        log.info("Loaded {} tenant configurations", cache.size());
    }

    private TenantAgentConfig loadConfig(String tenantId) {
        // Get default agent config
        var agents = agentProperties.agents();
        AgentProperties.AgentConfig defaultConfig = agents != null
                ? agents.getOrDefault(agentProperties.defaultAgent(), AgentProperties.AgentConfig.DEFAULT)
                : AgentProperties.AgentConfig.DEFAULT;

        // In SINGLE mode or if no config locations, build from defaults
        if (tenantConfig.mode() != TenantMode.MULTI || tenantConfig.configLocations().isEmpty()) {
            return TenantAgentConfig.fromDefaults(tenantId, defaultConfig);
        }

        // MULTI mode: search config locations for tenant YAML
        for (String location : tenantConfig.configLocations()) {
            String basePath = location.endsWith("/") ? location : location + "/";
            String yamlPath = basePath + tenantId + ".yml";
            String envPath = basePath + tenantId + ".env";

            Resource yamlResource = resourceLoader.getResource(yamlPath);
            if (yamlResource.exists()) {
                Map<String, String> envVars = envLoader.load(envPath);
                TenantAgentConfig tenantCfg = parseYaml(yamlResource, envVars, tenantId, defaultConfig);
                if (tenantCfg != null) {
                    return tenantCfg;
                }
            }
        }

        // No tenant-specific config found — fall back to defaults
        log.debug("No tenant YAML found for '{}', using defaults", tenantId);
        return TenantAgentConfig.fromDefaults(tenantId, defaultConfig);
    }

    private void scanLocation(String location) {
        String basePath = location.endsWith("/") ? location : location + "/";
        String pattern = basePath + "*.yml";

        try {
            ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename != null && filename.endsWith(".yml")) {
                    String tenantId = filename.substring(0, filename.length() - 4);
                    if (!cache.containsKey(tenantId)) {
                        resolve(tenantId);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan config location {}: {}", location, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private TenantAgentConfig parseYaml(Resource yamlResource, Map<String, String> envVars,
                                         String tenantId, AgentProperties.AgentConfig defaults) {
        try (InputStream is = yamlResource.getInputStream()) {
            String rawYaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String resolvedYaml = resolveEnvVars(rawYaml, envVars);

            Yaml yaml = new Yaml();
            Map<String, Object> doc = yaml.load(resolvedYaml);
            if (doc == null) {
                return null;
            }

            String cfgTenantId = getStr(doc, "tenant-id", tenantId);
            String name = getStr(doc, "name", defaults.name());
            String agentId = getStr(doc, "agent-id", defaults.id());

            // LLM — shallow merge: if present in tenant YAML, use entirely; otherwise default
            LlmConfig llm = parseLlm(getMap(doc, "llm"), defaults);

            // System prompt
            SystemPromptConfig systemPrompt = parseSystemPrompt(getMap(doc, "system-prompt"), defaults);

            // Tools
            AgentProperties.ToolPolicyConfig tools = parseTools(getMap(doc, "tools"), defaults);

            // Skills
            List<String> skills = getStringList(doc, "skills", defaults.skills());

            // MCP servers
            List<McpServerRef> mcpServers = parseMcpServers(getList(doc, "mcp-servers"), defaults);

            // Channels
            TenantChannelsConfig channels = parseChannels(getMap(doc, "channels"), defaults);

            // Features
            FeatureFlags features = parseFeatures(getMap(doc, "features"), defaults);

            // Error messages
            ErrorMessages errorMessages = parseErrorMessages(getMap(doc, "error-messages"), defaults);

            // Identity
            IdentityProperties identity = parseIdentity(getMap(doc, "identity"), defaults);

            // Tool loop
            ToolLoopProperties toolLoop = parseToolLoop(getMap(doc, "tool-loop"), defaults);

            // Loop delegate
            AgentLoopDelegateConfig loopDelegate = parseLoopDelegate(getMap(doc, "loop-delegate"), defaults);

            log.info("Loaded tenant config from YAML: {} ({})", cfgTenantId, yamlResource.getDescription());
            return new TenantAgentConfig(
                    cfgTenantId, agentId, name, llm, systemPrompt, tools, skills,
                    mcpServers, channels, features, errorMessages, identity, toolLoop, loopDelegate
            );
        } catch (IOException e) {
            log.error("Failed to parse tenant YAML for {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    private String resolveEnvVars(String yaml, Map<String, String> envVars) {
        if (envVars.isEmpty()) return yaml;

        Matcher matcher = ENV_VAR_PATTERN.matcher(yaml);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = envVars.getOrDefault(varName, System.getenv(varName));
            matcher.appendReplacement(sb, value != null ? Matcher.quoteReplacement(value) : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // --- Field parsers with defaults fallback ---

    private LlmConfig parseLlm(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.llm() != null ? defaults.llm() : LlmConfig.DEFAULT;
        }
        return new LlmConfig(
                getStr(map, "provider", null),
                getStr(map, "primary", null),
                getStringList(map, "fallbacks", List.of()),
                getStr(map, "thinking-model", null),
                getDouble(map, "temperature", 0.7),
                getInt(map, "max-tokens", 4096),
                getInt(map, "timeout-seconds", 120)
        );
    }

    private SystemPromptConfig parseSystemPrompt(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.systemPrompt() != null ? defaults.systemPrompt() : SystemPromptConfig.DEFAULT;
        }
        return new SystemPromptConfig(
                getStr(map, "strategy", "inline"),
                getStr(map, "content", null),
                getStr(map, "source", null),
                getBool(map, "append", false)
        );
    }

    private AgentProperties.ToolPolicyConfig parseTools(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.tools() != null ? defaults.tools() : AgentProperties.ToolPolicyConfig.DEFAULT;
        }
        return new AgentProperties.ToolPolicyConfig(
                getStr(map, "profile", "coding"),
                getStringList(map, "allow", List.of()),
                getStringList(map, "deny", List.of())
        );
    }

    @SuppressWarnings("unchecked")
    private List<McpServerRef> parseMcpServers(List<Object> list, AgentProperties.AgentConfig defaults) {
        if (list == null) {
            return defaults.mcpServers() != null ? defaults.mcpServers() : List.of();
        }
        List<McpServerRef> refs = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> map = (Map<String, Object>) m;
                refs.add(new McpServerRef(
                        getStr(map, "name", null),
                        getStr(map, "url", null),
                        getStr(map, "type", null),
                        getStr(map, "command", null),
                        getStringList(map, "args", List.of()),
                        getStr(map, "auth-token", null),
                        getBool(map, "enabled", true),
                        getStringList(map, "tools", List.of())
                ));
            }
        }
        return List.copyOf(refs);
    }

    @SuppressWarnings("unchecked")
    private TenantChannelsConfig parseChannels(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.channels() != null ? defaults.channels() : TenantChannelsConfig.EMPTY;
        }

        var telegramMap = getMap(map, "telegram");
        TenantChannelsConfig.TelegramChannelConfig telegram = telegramMap != null
                ? new TenantChannelsConfig.TelegramChannelConfig(
                    getStr(telegramMap, "bot-token", null),
                    getStr(telegramMap, "webhook-url", null),
                    getStr(telegramMap, "allowed-users", null),
                    getBool(telegramMap, "enabled", false))
                : null;

        var slackMap = getMap(map, "slack");
        TenantChannelsConfig.SlackChannelConfig slack = slackMap != null
                ? new TenantChannelsConfig.SlackChannelConfig(
                    getStr(slackMap, "bot-token", null),
                    getStr(slackMap, "signing-secret", null),
                    getStr(slackMap, "app-token", null),
                    getStr(slackMap, "allowed-senders", null),
                    getBool(slackMap, "enabled", false))
                : null;

        var discordMap = getMap(map, "discord");
        TenantChannelsConfig.DiscordChannelConfig discord = discordMap != null
                ? new TenantChannelsConfig.DiscordChannelConfig(
                    getStr(discordMap, "bot-token", null),
                    getStr(discordMap, "application-id", null),
                    getBool(discordMap, "enabled", false))
                : null;

        var smsMap = getMap(map, "sms");
        TenantChannelsConfig.SmsChannelConfig sms = smsMap != null
                ? new TenantChannelsConfig.SmsChannelConfig(
                    getStr(smsMap, "account-sid", null),
                    getStr(smsMap, "auth-token", null),
                    getStr(smsMap, "from-number", null),
                    getBool(smsMap, "enabled", false))
                : null;

        var emailMap = getMap(map, "email");
        TenantChannelsConfig.EmailChannelConfig email = emailMap != null
                ? new TenantChannelsConfig.EmailChannelConfig(
                    getStr(emailMap, "imap-host", null),
                    getInt(emailMap, "imap-port", 993),
                    getStr(emailMap, "smtp-host", null),
                    getInt(emailMap, "smtp-port", 587),
                    getStr(emailMap, "username", null),
                    getStr(emailMap, "password", null),
                    getBool(emailMap, "enabled", false))
                : null;

        var signalMap = getMap(map, "signal");
        TenantChannelsConfig.SignalChannelConfig signal = signalMap != null
                ? new TenantChannelsConfig.SignalChannelConfig(
                    getStr(signalMap, "phone-number", null),
                    getStr(signalMap, "api-url", null),
                    getBool(signalMap, "enabled", false))
                : null;

        var teamsMap = getMap(map, "teams");
        TenantChannelsConfig.TeamsChannelConfig teams = teamsMap != null
                ? new TenantChannelsConfig.TeamsChannelConfig(
                    getStr(teamsMap, "app-id", null),
                    getStr(teamsMap, "app-secret", null),
                    getStr(teamsMap, "tenant-id", null),
                    getBool(teamsMap, "enabled", false))
                : null;

        return new TenantChannelsConfig(telegram, slack, discord, sms, email, signal, teams);
    }

    private FeatureFlags parseFeatures(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.features() != null ? defaults.features() : FeatureFlags.DEFAULT;
        }
        return new FeatureFlags(
                getBool(map, "streaming", true),
                getInt(map, "history-length", 50),
                getBool(map, "tool-use", true),
                getBool(map, "multi-turn", true),
                getBool(map, "memory-enabled", true),
                getBool(map, "compaction-enabled", true)
        );
    }

    private ErrorMessages parseErrorMessages(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.errorMessages() != null ? defaults.errorMessages() : ErrorMessages.DEFAULT;
        }
        return new ErrorMessages(
                getStr(map, "empty-response", ErrorMessages.DEFAULT.emptyResponse()),
                getStr(map, "provider-error", ErrorMessages.DEFAULT.providerError()),
                getStr(map, "max-iterations", ErrorMessages.DEFAULT.maxIterations()),
                getStr(map, "tool-execution-error", ErrorMessages.DEFAULT.toolExecutionError()),
                getStr(map, "general-error", ErrorMessages.DEFAULT.generalError()),
                getStr(map, "timeout", ErrorMessages.DEFAULT.timeout()),
                getStr(map, "no-tenant-context", ErrorMessages.DEFAULT.noTenantContext())
        );
    }

    private IdentityProperties parseIdentity(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.identity() != null ? defaults.identity() : IdentityProperties.DEFAULT;
        }
        return new IdentityProperties(
                getStr(map, "name", IdentityProperties.DEFAULT.name()),
                getStr(map, "description", IdentityProperties.DEFAULT.description())
        );
    }

    private ToolLoopProperties parseToolLoop(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.toolLoop() != null ? defaults.toolLoop() : ToolLoopProperties.DEFAULT;
        }
        return new ToolLoopProperties(
                getStr(map, "mode", "spring-ai"),
                getInt(map, "max-iterations", 25),
                getBool(map, "require-approval", false)
        );
    }

    private AgentLoopDelegateConfig parseLoopDelegate(Map<String, Object> map, AgentProperties.AgentConfig defaults) {
        if (map == null) {
            return defaults.loopDelegate() != null ? defaults.loopDelegate() : AgentLoopDelegateConfig.DISABLED;
        }

        Map<String, String> props = new LinkedHashMap<>();
        Map<String, Object> propsMap = getMap(map, "properties");
        if (propsMap != null) {
            propsMap.forEach((k, v) -> props.put(k, v != null ? v.toString() : ""));
        }

        return new AgentLoopDelegateConfig(
                getBool(map, "enabled", false),
                getStr(map, "delegate-id", null),
                getStr(map, "workflow", null),
                getStr(map, "llm-role", null),
                getInt(map, "timeout-seconds", 120),
                props
        );
    }

    // --- YAML map utility methods ---

    private static String getStr(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private static boolean getBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof List<?> l ? (List<Object>) l : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Map<String, Object> map, String key, List<String> defaultVal) {
        Object val = map.get(key);
        if (val instanceof List<?> l) {
            return l.stream().map(Object::toString).toList();
        }
        return defaultVal;
    }
}
