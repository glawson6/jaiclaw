package io.jaiclaw.shell.commands.setup;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class OnboardResult {

    public enum FlowMode { QUICKSTART, MANUAL }
    public enum McpTransportType { STDIO, SSE, HTTP }

    // Flow
    private FlowMode flowMode = FlowMode.QUICKSTART;
    private ExistingConfigAction existingConfigAction = ExistingConfigAction.NONE;

    // LLM
    private String llmProvider;       // "openai" | "anthropic" | "ollama" | "bedrock"
    private String llmApiKey;         // null for ollama/bedrock
    private String llmModel;          // e.g. "gpt-4o"
    private String ollamaBaseUrl = "http://localhost:11434";
    private String awsRegion = "us-east-1";

    // Security
    private String securityMode = "api-key";  // "api-key" | "jwt" | "none"
    private String apiKey;                     // custom API key (null = auto-generate)

    // Gateway
    private int serverPort = 8080;
    private String bindAddress = "0.0.0.0";
    private String assistantName = "JaiClaw";

    // Channels
    private TelegramConfig telegram;
    private SlackConfig slack;
    private DiscordConfig discord;

    // Skills
    private SkillsConfig skillsConfig;

    // MCP Servers
    private List<McpServerConfig> mcpServers = new ArrayList<>();

    // 1Password integration (optional; null = not configured)
    private OnePasswordConfig onePassword;

    // Optional REPL prompt format (null = leave the default `${identity} > `)
    private String promptFormat;

    // Config output
    private Path configDir;

    public enum ExistingConfigAction { NONE, KEEP, MODIFY, RESET }

    public record TelegramConfig(String botToken, boolean enabled) {}
    public record SlackConfig(String botToken, String signingSecret, String appToken, boolean enabled) {}
    public record DiscordConfig(String botToken, String applicationId, boolean enabled) {}
    public record SkillsConfig(List<String> enabledBundled, String workspaceDir) {}
    public record McpServerConfig(
            String name, String description, McpTransportType transportType,
            String command, List<String> args,   // stdio
            String url,                          // sse/http
            String authToken                     // http (nullable)
    ) {}

    /**
     * Captures the operator's choice to scaffold a 1Password template
     * during setup. The actual {@code .env.op.tpl} is written by
     * {@code OnePasswordTemplateWriter} in the FinalizationStep.
     *
     * <p>Contains no secret values — only the vault name and the list
     * of env-var names the operator wants to migrate. The mapping
     * uses a "same-vault, one-item-per-bundle" heuristic where the
     * item title is derived from the env-var prefix (e.g.
     * {@code ANTHROPIC_API_KEY} → item "Anthropic", field "api-key").
     * Operators who need different shapes can edit the generated
     * file directly or use the {@code bin/jaiclaw setup-1password}
     * fast-path command for finer control.
     */
    public record OnePasswordConfig(String vault, List<String> envKeys) {}

    // --- Getters and setters ---

    public FlowMode flowMode() { return flowMode; }
    public void setFlowMode(FlowMode flowMode) { this.flowMode = flowMode; }

    public ExistingConfigAction existingConfigAction() { return existingConfigAction; }
    public void setExistingConfigAction(ExistingConfigAction action) { this.existingConfigAction = action; }

    public String llmProvider() { return llmProvider; }
    public void setLlmProvider(String llmProvider) { this.llmProvider = llmProvider; }

    public String llmApiKey() { return llmApiKey; }
    public void setLlmApiKey(String llmApiKey) { this.llmApiKey = llmApiKey; }

    public String llmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }

    public String ollamaBaseUrl() { return ollamaBaseUrl; }
    public void setOllamaBaseUrl(String ollamaBaseUrl) { this.ollamaBaseUrl = ollamaBaseUrl; }

    public String awsRegion() { return awsRegion; }
    public void setAwsRegion(String awsRegion) { this.awsRegion = awsRegion; }

    public String securityMode() { return securityMode; }
    public void setSecurityMode(String securityMode) { this.securityMode = securityMode; }

    public String apiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public int serverPort() { return serverPort; }
    public void setServerPort(int serverPort) { this.serverPort = serverPort; }

    public String bindAddress() { return bindAddress; }
    public void setBindAddress(String bindAddress) { this.bindAddress = bindAddress; }

    public String assistantName() { return assistantName; }
    public void setAssistantName(String assistantName) { this.assistantName = assistantName; }

    public TelegramConfig telegram() { return telegram; }
    public void setTelegram(TelegramConfig telegram) { this.telegram = telegram; }

    public SlackConfig slack() { return slack; }
    public void setSlack(SlackConfig slack) { this.slack = slack; }

    public DiscordConfig discord() { return discord; }
    public void setDiscord(DiscordConfig discord) { this.discord = discord; }

    public Path configDir() { return configDir; }
    public void setConfigDir(Path configDir) { this.configDir = configDir; }

    public SkillsConfig skillsConfig() { return skillsConfig; }
    public void setSkillsConfig(SkillsConfig skillsConfig) { this.skillsConfig = skillsConfig; }

    public List<McpServerConfig> mcpServers() { return mcpServers; }
    public void setMcpServers(List<McpServerConfig> mcpServers) { this.mcpServers = mcpServers; }

    public OnePasswordConfig onePassword() { return onePassword; }
    public void setOnePassword(OnePasswordConfig onePassword) { this.onePassword = onePassword; }

    public String promptFormat() { return promptFormat; }
    public void setPromptFormat(String promptFormat) { this.promptFormat = promptFormat; }

    public boolean isManual() { return flowMode == FlowMode.MANUAL; }
}
