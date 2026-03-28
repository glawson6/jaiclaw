package io.jaiclaw.agent.tenant;

import io.jaiclaw.config.TenantAgentConfig;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Factory for creating per-tenant {@link ChatModel} instances.
 * Implementations cache models by tenant ID to avoid re-creation on each request.
 */
public interface TenantChatModelFactory {

    /**
     * Create a new ChatModel for the given tenant config.
     */
    ChatModel createChatModel(TenantAgentConfig config);

    /**
     * Get or create a cached ChatModel for the given tenant.
     */
    ChatModel getOrCreate(String tenantId, TenantAgentConfig config);

    /**
     * Evict the cached ChatModel for a tenant (e.g., on config reload).
     */
    void evict(String tenantId);
}
