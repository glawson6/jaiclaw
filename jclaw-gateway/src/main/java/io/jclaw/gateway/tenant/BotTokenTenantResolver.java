package io.jclaw.gateway.tenant;

import io.jclaw.core.tenant.DefaultTenantContext;
import io.jclaw.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the tenant from a channel identifier — bot token, workspace ID, guild ID, or phone number.
 * Uses a configurable mapping table populated at startup or via admin API.
 * <p>
 * Attribute keys checked (in order):
 * <ul>
 *   <li>{@code channelId} — generic channel identifier</li>
 *   <li>{@code botToken} — Telegram bot token</li>
 *   <li>{@code teamId} — Slack workspace ID</li>
 *   <li>{@code guildId} — Discord server ID</li>
 *   <li>{@code fromNumber} — SMS sender phone number</li>
 * </ul>
 */
public class BotTokenTenantResolver implements TenantResolver {

    private static final Logger log = LoggerFactory.getLogger(BotTokenTenantResolver.class);

    private static final String[] LOOKUP_KEYS = {
            "channelId", "botToken", "teamId", "guildId", "fromNumber"
    };

    private final Map<String, TenantContext> mappings = new ConcurrentHashMap<>();

    /**
     * Register a mapping from a channel identifier to a tenant.
     */
    public void register(String channelIdentifier, TenantContext tenant) {
        mappings.put(channelIdentifier, tenant);
        log.debug("Registered tenant mapping: {} -> {}", channelIdentifier, tenant.getTenantId());
    }

    /**
     * Register a mapping from a channel identifier to a tenant ID and name.
     */
    public void register(String channelIdentifier, String tenantId, String tenantName) {
        register(channelIdentifier, new DefaultTenantContext(tenantId, tenantName));
    }

    @Override
    public Optional<TenantContext> resolve(Map<String, String> attributes) {
        for (String key : LOOKUP_KEYS) {
            String value = attributes.get(key);
            if (value != null) {
                TenantContext tenant = mappings.get(value);
                if (tenant != null) {
                    log.debug("Bot-token tenant resolved via {}: {} -> {}",
                            key, value, tenant.getTenantId());
                    return Optional.of(tenant);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int order() {
        return 20;
    }

    public int mappingCount() {
        return mappings.size();
    }
}
