package io.jaiclaw.session.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for {@link RedisSessionManager}. Bound under
 * {@code jaiclaw.agent.session.redis}.
 *
 * @param prefix Redis key prefix — every session key is
 *               {@code {prefix}:{tenantId}:{sessionKey}}. Defaults to
 *               {@code "jaiclaw:sessions"}.
 * @param ttl    per-session TTL applied to the metadata + messages keys
 *               on every write. Defaults to 30 days.
 */
@ConfigurationProperties(prefix = "jaiclaw.agent.session.redis")
public record RedisSessionProperties(
        String prefix,
        Duration ttl
) {
    public RedisSessionProperties {
        if (prefix == null || prefix.isBlank()) prefix = "jaiclaw:sessions";
        if (ttl == null) ttl = Duration.ofDays(30);
    }
}
