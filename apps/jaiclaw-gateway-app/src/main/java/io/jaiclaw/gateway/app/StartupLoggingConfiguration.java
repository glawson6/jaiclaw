package io.jaiclaw.gateway.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class StartupLoggingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StartupLoggingConfiguration.class);

    @Bean
    ApplicationRunner aiProviderStartupLogger(Environment env, ChatModel chatModel) {
        return args -> {
            String provider = env.getProperty("spring.ai.model.chat", "anthropic");
            String model = resolveModel(env, provider);
            String chatModelClass = chatModel.getClass().getSimpleName();

            log.info("AI Provider: {} | Model: {} | ChatModel: {}", provider, model, chatModelClass);
        };
    }

    private String resolveModel(Environment env, String provider) {
        return switch (provider) {
            case "anthropic" -> env.getProperty("spring.ai.anthropic.chat.options.model", "claude-haiku-4-5-20251001");
            case "openai" -> env.getProperty("spring.ai.openai.chat.options.model", "gpt-4o");
            case "minimax" -> env.getProperty("spring.ai.minimax.chat.options.model", "M2-her");
            case "ollama" -> env.getProperty("spring.ai.ollama.chat.options.model", "llama3");
            case "google-genai" -> env.getProperty("spring.ai.google.genai.chat.options.model", "gemini-2.0-flash");
            case "bedrock" -> env.getProperty("spring.ai.bedrock.converse.chat.options.model", "us.anthropic.claude-3-5-sonnet-20241022-v2:0");
            case "vertex-ai" -> env.getProperty("spring.ai.vertex-ai.chat.options.model", "gemini-pro");
            case "deepseek" -> env.getProperty("spring.ai.deepseek.chat.options.model", "deepseek-chat");
            case "mistral-ai" -> env.getProperty("spring.ai.mistralai.chat.options.model", "mistral-large-latest");
            case "oci-genai" -> env.getProperty("spring.ai.oci.genai.chat.options.model", "cohere.command-r-plus");
            default -> "unknown";
        };
    }
}
