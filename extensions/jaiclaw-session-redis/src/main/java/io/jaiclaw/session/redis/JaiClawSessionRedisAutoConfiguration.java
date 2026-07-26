package io.jaiclaw.session.redis;

import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.core.tenant.TenantProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuration for the Redis-backed {@link SessionManager}.
 *
 * <p>Activates when all of:
 * <ul>
 *   <li>{@link StringRedisTemplate} is on the classpath (Spring Data
 *       Redis pulled in by the consuming app).</li>
 *   <li>{@code jaiclaw.agent.session.backend=redis} is set — no default,
 *       explicit opt-in.</li>
 *   <li>No adopter-supplied {@code SessionManager} bean is already present.</li>
 * </ul>
 *
 * <p>When any condition fails the framework's default
 * {@code InMemorySessionManager} (registered in
 * {@code JaiClawAgentAutoConfiguration}) continues to win.
 */
@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "jaiclaw.agent.session", name = "backend",
        havingValue = "redis")
@EnableConfigurationProperties(RedisSessionProperties.class)
public class JaiClawSessionRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SessionManager.class)
    public SessionManager sessionManager(StringRedisTemplate template,
                                          ObjectProvider<TenantGuard> tenantGuardProvider,
                                          ObjectProvider<AgentHookDispatcher> hookProvider,
                                          RedisSessionProperties props) {
        TenantGuard tenantGuard = tenantGuardProvider.getIfAvailable(
                () -> new TenantGuard(TenantProperties.DEFAULT));
        RedisSessionManager manager = new RedisSessionManager(
                template, tenantGuard, props.prefix(), props.ttl());
        AgentHookDispatcher hooks = hookProvider.getIfAvailable();
        if (hooks != null) manager.setHookDispatcher(hooks);
        return manager;
    }
}
