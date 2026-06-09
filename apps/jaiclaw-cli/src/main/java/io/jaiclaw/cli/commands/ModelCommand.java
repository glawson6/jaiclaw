package io.jaiclaw.cli.commands;

import io.jaiclaw.config.JaiClawProperties;
import io.jaiclaw.config.ModelsProperties.ModelProviderConfig;

import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.Map;

/**
 * Model management commands — list providers and available models.
 */
@ShellComponent
public class ModelCommand {

    private final JaiClawProperties properties;

    public ModelCommand(JaiClawProperties properties) {
        this.properties = properties;
    }

    @ShellMethod(key = {"model list", "model-list"}, value = "List configured LLM providers and models")
    public String list() {
        Map<String, ModelProviderConfig> providers = properties.models().providers();
        if (providers.isEmpty()) {
            return "No LLM providers configured.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("LLM Providers & Models\n");
        sb.append("======================\n\n");

        for (Map.Entry<String, ModelProviderConfig> entry : providers.entrySet()) {
            String key = entry.getKey();
            ModelProviderConfig config = entry.getValue();

            String displayName = config.displayName() != null ? config.displayName() : key;
            String envVar = providerEnvVar(key);
            boolean configured = isProviderConfigured(key);
            String status = configured ? "configured" : "not configured";

            sb.append("  %s (%s) [%s]\n".formatted(displayName, key, status));

            if (config.fallbackModel() != null) {
                sb.append("    Fallback: %s\n".formatted(config.fallbackModel()));
            }

            List<String> models = config.wizardModels();
            if (models != null && !models.isEmpty()) {
                sb.append("    Models:   %s\n".formatted(String.join(", ", models)));
            }

            if (envVar != null) {
                sb.append("    Env var:  %s\n".formatted(envVar));
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    @ShellMethod(key = {"model show", "model-show"}, value = "Show the currently active model configuration")
    public String show() {
        String provider = System.getenv("AI_PROVIDER");
        if (provider == null || provider.isBlank()) {
            provider = "anthropic";
        }

        String model = resolveActiveModel(provider);

        StringBuilder sb = new StringBuilder();
        sb.append("Active Model\n");
        sb.append("============\n");
        sb.append("  Provider: %s\n".formatted(provider));
        sb.append("  Model:    %s\n".formatted(model));
        return sb.toString();
    }

    private String resolveActiveModel(String provider) {
        return switch (provider) {
            case "openai" -> envOrDefault("OPENAI_MODEL", "gpt-4o");
            case "anthropic" -> envOrDefault("ANTHROPIC_MODEL", "claude-sonnet-4-5");
            case "ollama" -> envOrDefault("OLLAMA_MODEL", "llama3");
            case "gemini" -> envOrDefault("GEMINI_MODEL", "gemini-2.0-flash");
            case "bedrock" -> envOrDefault("BEDROCK_MODEL", "us.anthropic.claude-3-5-sonnet-20241022-v2:0");
            default -> "(unknown)";
        };
    }

    private boolean isProviderConfigured(String provider) {
        return switch (provider) {
            case "openai" -> envSet("OPENAI_API_KEY");
            case "anthropic" -> envSet("ANTHROPIC_API_KEY");
            case "gemini" -> envSet("GEMINI_API_KEY");
            case "bedrock" -> envSet("AWS_ACCESS_KEY_ID") || envSet("AWS_REGION");
            case "ollama" -> true; // Always available locally
            default -> false;
        };
    }

    private String providerEnvVar(String provider) {
        return switch (provider) {
            case "openai" -> "OPENAI_API_KEY";
            case "anthropic" -> "ANTHROPIC_API_KEY";
            case "gemini" -> "GEMINI_API_KEY";
            case "bedrock" -> "AWS_ACCESS_KEY_ID";
            case "ollama" -> "OLLAMA_BASE_URL";
            default -> null;
        };
    }

    private static boolean envSet(String name) {
        String val = System.getenv(name);
        return val != null && !val.isBlank() && !"not-set".equals(val);
    }

    private static String envOrDefault(String name, String defaultValue) {
        String val = System.getenv(name);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}
