package io.jaiclaw.agent.tenant;

import io.jaiclaw.config.LlmConfig;
import io.jaiclaw.config.ModelsProperties;
import io.jaiclaw.config.TenantAgentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Default implementation of {@link TenantChatModelFactory} that creates ChatModel
 * instances per tenant using the configured LLM provider and model.
 *
 * <p>Delegates actual ChatModel creation to a pluggable factory function, since
 * creating Anthropic/OpenAI/Ollama models requires provider-specific Spring AI
 * classes that are optional dependencies.
 */
public class DefaultTenantChatModelFactory implements TenantChatModelFactory {

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantChatModelFactory.class);

    private final ModelsProperties modelsProperties;
    private final Function<ChatModelRequest, ChatModel> modelCreator;
    private final ConcurrentHashMap<String, ChatModel> cache = new ConcurrentHashMap<>();

    /**
     * @param modelsProperties global model provider configurations
     * @param modelCreator     factory function that creates a ChatModel from a request
     */
    public DefaultTenantChatModelFactory(ModelsProperties modelsProperties,
                                          Function<ChatModelRequest, ChatModel> modelCreator) {
        this.modelsProperties = modelsProperties;
        this.modelCreator = modelCreator;
    }

    @Override
    public ChatModel createChatModel(TenantAgentConfig config) {
        LlmConfig llm = config.llm();
        String provider = llm.provider();

        // Resolve provider config (API key, base URL) from global providers
        ModelsProperties.ModelProviderConfig providerConfig = null;
        if (provider != null && modelsProperties.providers() != null) {
            providerConfig = modelsProperties.providers().get(provider);
        }

        ChatModelRequest request = new ChatModelRequest(
                provider,
                llm.primary(),
                llm.temperature(),
                llm.maxTokens(),
                llm.timeoutSeconds(),
                providerConfig
        );

        log.info("Creating ChatModel for tenant '{}': provider={}, model={}",
                config.tenantId(), provider, llm.primary());

        return modelCreator.apply(request);
    }

    @Override
    public ChatModel getOrCreate(String tenantId, TenantAgentConfig config) {
        return cache.computeIfAbsent(tenantId, id -> createChatModel(config));
    }

    @Override
    public void evict(String tenantId) {
        cache.remove(tenantId);
        log.debug("Evicted cached ChatModel for tenant: {}", tenantId);
    }

    /**
     * Request object passed to the model creator function.
     */
    public record ChatModelRequest(
            String provider,
            String modelId,
            double temperature,
            int maxTokens,
            int timeoutSeconds,
            ModelsProperties.ModelProviderConfig providerConfig
    ) {

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String provider;
            private String modelId;
            private double temperature;
            private int maxTokens;
            private int timeoutSeconds;
            private ModelsProperties.ModelProviderConfig providerConfig;

            public Builder provider(String provider) { this.provider = provider; return this; }
            public Builder modelId(String modelId) { this.modelId = modelId; return this; }
            public Builder temperature(double temperature) { this.temperature = temperature; return this; }
            public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
            public Builder timeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; return this; }
            public Builder providerConfig(ModelsProperties.ModelProviderConfig providerConfig) { this.providerConfig = providerConfig; return this; }

            public ChatModelRequest build() {
                return new ChatModelRequest(
                        provider, modelId, temperature, maxTokens, timeoutSeconds, providerConfig);
            }
        }
}
}
