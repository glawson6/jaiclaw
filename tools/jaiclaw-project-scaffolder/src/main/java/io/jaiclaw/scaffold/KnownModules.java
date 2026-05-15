package io.jaiclaw.scaffold;

import java.util.Set;

/**
 * Central registry of valid JaiClaw module names for manifest validation.
 * Update this class when new modules are added to the monorepo.
 */
public final class KnownModules {

    private KnownModules() {}

    public static final Set<String> EXTENSIONS = Set.of(
            "audit", "browser", "calendar", "camel", "canvas", "code",
            "compaction", "cron", "cron-manager", "discord-tools",
            "docs", "docstore", "docstore-telegram", "documents",
            "embabel-delegate", "identity", "media", "messaging",
            "plugin-sdk", "security", "slack-tools", "subscription",
            "subscription-telegram", "tools-k8s", "tools-security",
            "voice", "voice-call"
    );

    public static final Set<String> CHANNELS = Set.of(
            "telegram", "slack", "discord", "email", "sms", "signal", "teams"
    );

    public static final Set<String> AI_PROVIDERS = Set.of(
            "anthropic", "openai", "ollama", "gemini", "bedrock",
            "azure-openai", "deepseek", "mistral", "minimax",
            "vertex-ai", "oci-genai"
    );

    public static final Set<String> TOOL_PROFILES = Set.of(
            "none", "minimal", "coding", "messaging", "full"
    );

    public static final Set<String> SECURITY_MODES = Set.of(
            "api-key", "jwt", "none"
    );

    public static final Set<String> PROMPT_STRATEGIES = Set.of(
            "none", "inline", "classpath"
    );

    public static final Set<String> PARENT_MODES = Set.of(
            "standalone", "jaiclaw"
    );

    /** Maps AI provider name to Spring AI starter artifact (groupId: org.springframework.ai). */
    public static String springAiStarterArtifact(String provider) {
        return switch (provider) {
            case "anthropic" -> "spring-ai-starter-model-anthropic";
            case "openai" -> "spring-ai-starter-model-openai";
            case "ollama" -> "spring-ai-starter-model-ollama";
            case "gemini", "vertex-ai" -> "spring-ai-starter-model-vertex-ai";
            case "bedrock" -> "spring-ai-starter-model-bedrock-ai";
            case "azure-openai" -> "spring-ai-starter-model-azure-openai";
            case "deepseek" -> "spring-ai-starter-model-openai"; // DeepSeek uses OpenAI-compatible API
            case "mistral" -> "spring-ai-starter-model-mistral-ai";
            case "minimax" -> "spring-ai-starter-model-minimax";
            case "oci-genai" -> "spring-ai-starter-model-oci-genai";
            default -> throw new IllegalArgumentException("Unknown AI provider: " + provider);
        };
    }

    /** Maps AI provider name to JaiClaw starter artifactId. */
    public static String jaiclawStarterArtifact(String provider) {
        return "jaiclaw-starter-" + provider;
    }
}
