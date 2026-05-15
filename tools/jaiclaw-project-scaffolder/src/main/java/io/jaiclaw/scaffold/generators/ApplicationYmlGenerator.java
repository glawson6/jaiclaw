package io.jaiclaw.scaffold.generators;

import io.jaiclaw.scaffold.ProjectManifest;
import io.jaiclaw.scaffold.ProjectManifest.Archetype;

/**
 * Generates application.yml with conditional sections per archetype/provider.
 */
public final class ApplicationYmlGenerator {

    private ApplicationYmlGenerator() {}

    public static String generate(ProjectManifest manifest) {
        var sb = new StringBuilder();

        // Server
        sb.append("server:\n");
        sb.append("  port: ${GATEWAY_PORT:").append(manifest.server().port()).append("}\n\n");

        // JaiClaw config
        sb.append("jaiclaw:\n");

        // Skills
        sb.append("  skills:\n");
        if (manifest.skills().allowBundled().isEmpty()) {
            sb.append("    allow-bundled: []\n");
        } else {
            sb.append("    allow-bundled: [").append(String.join(", ", manifest.skills().allowBundled())).append("]\n");
        }

        // Security
        if (!"none".equals(manifest.security().mode())) {
            sb.append("  security:\n");
            sb.append("    mode: ${JAICLAW_SECURITY_MODE:").append(manifest.security().mode()).append("}\n");
            if ("api-key".equals(manifest.security().mode())) {
                sb.append("    api-key: ${JAICLAW_API_KEY:}\n");
            }
        }

        // Identity
        sb.append("  identity:\n");
        sb.append("    name: ").append(ProjectManifest.toPascalCase(manifest.name())).append("\n");
        sb.append("    description: ").append(manifest.description()).append("\n");

        // Agent
        sb.append("  agent:\n");
        sb.append("    default-agent: default\n");
        sb.append("    agents:\n");
        sb.append("      default:\n");
        sb.append("        id: default\n");
        sb.append("        name: ").append(manifest.agent().name()).append("\n");
        sb.append("        tools:\n");
        sb.append("          profile: ").append(manifest.agent().toolsProfile()).append("\n");

        // System prompt
        if (manifest.agent().systemPrompt() != null) {
            String strategy = manifest.agent().systemPrompt().strategy();
            if ("classpath".equals(strategy) && manifest.agent().systemPrompt().source() != null) {
                sb.append("        system-prompt:\n");
                sb.append("          source: ").append(manifest.agent().systemPrompt().source()).append("\n");
            } else if ("inline".equals(strategy) && manifest.agent().systemPrompt().content() != null
                    && !manifest.agent().systemPrompt().content().isBlank()) {
                sb.append("        system-prompt:\n");
                sb.append("          content: |\n");
                for (String line : manifest.agent().systemPrompt().content().split("\n")) {
                    sb.append("            ").append(line).append("\n");
                }
            }
        }

        // Camel config
        if (manifest.archetype() == Archetype.CAMEL && manifest.camel() != null) {
            sb.append("  camel:\n");
            sb.append("    channel-id: ").append(manifest.camel().channelId()).append("\n");
            sb.append("    display-name: ").append(manifest.camel().displayName()).append("\n");
            sb.append("    stateless: ").append(manifest.camel().stateless()).append("\n");
        }

        sb.append("\n");

        // Spring config
        sb.append("spring:\n");

        // Embabel exclusion (unless archetype is embabel)
        if (manifest.archetype() != Archetype.EMBABEL) {
            sb.append("  autoconfigure:\n");
            sb.append("    exclude:\n");
            sb.append("      - com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration\n");
        }

        // AI provider config
        sb.append("  ai:\n");
        sb.append("    model:\n");
        sb.append("      chat: ${AI_PROVIDER:").append(manifest.aiProvider().primary()).append("}\n");

        appendProviderConfig(sb, manifest.aiProvider().primary(), true);
        for (String additional : manifest.aiProvider().additional()) {
            appendProviderConfig(sb, additional, false);
        }

        // Embabel config
        if (manifest.archetype() == Archetype.EMBABEL && manifest.embabel() != null) {
            sb.append("\nembabel:\n");
            sb.append("  models:\n");
            sb.append("    default-llm: ").append(manifest.embabel().defaultLlm()).append("\n");
        }

        return sb.toString();
    }

    private static void appendProviderConfig(StringBuilder sb, String provider, boolean enabled) {
        String enabledStr = enabled ? "true" : "false";
        String envPrefix = provider.toUpperCase().replace("-", "_");

        switch (provider) {
            case "anthropic" -> {
                sb.append("    anthropic:\n");
                sb.append("      enabled: ${ANTHROPIC_ENABLED:").append(enabledStr).append("}\n");
                sb.append("      api-key: ${ANTHROPIC_API_KEY:not-set}\n");
                sb.append("      chat:\n");
                sb.append("        options:\n");
                sb.append("          model: ${ANTHROPIC_MODEL:claude-sonnet-4-5}\n");
            }
            case "openai" -> {
                sb.append("    openai:\n");
                sb.append("      enabled: ${OPENAI_ENABLED:").append(enabledStr).append("}\n");
                sb.append("      api-key: ${OPENAI_API_KEY:not-set}\n");
            }
            case "ollama" -> {
                sb.append("    ollama:\n");
                sb.append("      enabled: ${OLLAMA_ENABLED:").append(enabledStr).append("}\n");
                sb.append("      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}\n");
            }
            case "gemini" -> {
                sb.append("    vertex-ai:\n");
                sb.append("      enabled: ${GEMINI_ENABLED:").append(enabledStr).append("}\n");
                sb.append("      project-id: ${GOOGLE_CLOUD_PROJECT:}\n");
                sb.append("      location: ${GOOGLE_CLOUD_LOCATION:us-central1}\n");
            }
            case "minimax" -> {
                sb.append("    minimax:\n");
                sb.append("      enabled: ${MINIMAX_ENABLED:").append(enabledStr).append("}\n");
                sb.append("      api-key: ${MINIMAX_API_KEY:not-set}\n");
                sb.append("      base-url: ${MINIMAX_BASE_URL:https://api.minimax.chat}\n");
                sb.append("      chat:\n");
                sb.append("        options:\n");
                sb.append("          model: ${MINIMAX_MODEL:M2-her}\n");
            }
            case "deepseek" -> {
                sb.append("    openai:\n");
                sb.append("      enabled: ${DEEPSEEK_ENABLED:").append(enabledStr).append("}\n");
                sb.append("      api-key: ${DEEPSEEK_API_KEY:not-set}\n");
                sb.append("      base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}\n");
            }
            case "mistral" -> {
                sb.append("    mistralai:\n");
                sb.append("      enabled: ${MISTRAL_ENABLED:").append(enabledStr).append("}\n");
                sb.append("      api-key: ${MISTRAL_API_KEY:not-set}\n");
            }
            case "bedrock" -> {
                sb.append("    bedrock:\n");
                sb.append("      enabled: ${BEDROCK_ENABLED:").append(enabledStr).append("}\n");
                sb.append("      aws:\n");
                sb.append("        region: ${AWS_REGION:us-east-1}\n");
            }
            case "azure-openai" -> {
                sb.append("    azure:\n");
                sb.append("      openai:\n");
                sb.append("        enabled: ${AZURE_OPENAI_ENABLED:").append(enabledStr).append("}\n");
                sb.append("        api-key: ${AZURE_OPENAI_API_KEY:not-set}\n");
                sb.append("        endpoint: ${AZURE_OPENAI_ENDPOINT:}\n");
            }
            default -> {
                sb.append("    # ").append(provider).append(" — configure manually\n");
            }
        }
    }
}
