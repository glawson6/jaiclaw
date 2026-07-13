package io.jaiclaw.cli.commands;

import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.tools.ToolRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Diagnostic command that checks JaiClaw's runtime environment.
 */
@Component
public class DoctorCommand {

    private final JaiClawProperties properties;
    private final ObjectProvider<ToolRegistry> toolRegistryProvider;

    public DoctorCommand(JaiClawProperties properties,
                         ObjectProvider<ToolRegistry> toolRegistryProvider) {
        this.properties = properties;
        this.toolRegistryProvider = toolRegistryProvider;
    }

    @Command(name = "doctor", alias = "doc", description = "Diagnose JaiClaw configuration and environment")
    public String doctor() {
        StringBuilder sb = new StringBuilder();
        sb.append("JaiClaw Doctor\n");
        sb.append("==============\n\n");

        // Java version
        sb.append(checkItem("Java", Runtime.version().toString(), true));

        // JAICLAW_HOME
        String jaiclawhome = System.getenv("JAICLAW_HOME");
        Path homeDir = jaiclawhome != null ? Path.of(jaiclawhome) : Path.of(System.getProperty("user.home"), ".jaiclaw");
        sb.append(checkItem("JAICLAW_HOME", homeDir.toString(), Files.isDirectory(homeDir)));

        // Config files
        Path localConfig = homeDir.resolve("application-local.yml");
        sb.append(checkItem("Local config", localConfig.toString(), Files.exists(localConfig)));

        Path envFile = homeDir.resolve(".env");
        sb.append(checkItem("Env file", envFile.toString(), Files.exists(envFile)));

        // LLM providers
        sb.append("\nLLM Providers:\n");
        sb.append(checkApiKey("OpenAI", "OPENAI_API_KEY"));
        sb.append(checkApiKey("Anthropic", "ANTHROPIC_API_KEY"));
        sb.append(checkApiKey("Gemini", "GEMINI_API_KEY"));
        sb.append(checkEnv("AWS Bedrock", "AWS_ACCESS_KEY_ID"));
        sb.append(checkOllama());

        // Channels
        sb.append("\nChannels:\n");
        sb.append(checkEnv("Telegram", "TELEGRAM_BOT_TOKEN"));
        sb.append(checkEnv("Slack", "SLACK_BOT_TOKEN"));
        sb.append(checkEnv("Discord", "DISCORD_BOT_TOKEN"));

        // Tools
        ToolRegistry toolRegistry = toolRegistryProvider.getIfAvailable();
        if (toolRegistry != null) {
            sb.append("\nTools:     %d registered\n".formatted(toolRegistry.size()));
        }

        // Docker
        sb.append("\nExternal:\n");
        sb.append(checkDockerAvailable());

        return sb.toString();
    }

    private String checkItem(String label, String detail, boolean ok) {
        String icon = ok ? "[ok]" : "[--]";
        return "  %s %-15s %s\n".formatted(icon, label, detail);
    }

    private String checkApiKey(String provider, String envVar) {
        String value = System.getenv(envVar);
        boolean configured = value != null && !value.isBlank() && !"not-set".equals(value);
        String detail = configured ? "configured" : "not configured";
        return checkItem(provider, detail, configured);
    }

    private String checkEnv(String label, String envVar) {
        String value = System.getenv(envVar);
        boolean configured = value != null && !value.isBlank();
        String detail = configured ? "configured" : "not configured";
        return checkItem(label, detail, configured);
    }

    private String checkOllama() {
        String ollamaUrl = System.getenv("OLLAMA_BASE_URL");
        if (ollamaUrl == null) {
            ollamaUrl = "http://localhost:11434";
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                    "--connect-timeout", "2", ollamaUrl);
            Process process = pb.start();
            int exitCode = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            boolean reachable = exitCode == 0 && "200".equals(output);
            return checkItem("Ollama", reachable ? "reachable at " + ollamaUrl : "not reachable", reachable);
        } catch (IOException | InterruptedException e) {
            return checkItem("Ollama", "not reachable", false);
        }
    }

    private String checkDockerAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            boolean ok = exitCode == 0 && !output.isBlank();
            return checkItem("Docker", ok ? "v" + output : "not available", ok);
        } catch (IOException | InterruptedException e) {
            return checkItem("Docker", "not available", false);
        }
    }
}
