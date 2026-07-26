package io.jaiclaw.session.redis

import io.jaiclaw.agent.session.SessionManager
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.MapPropertySource
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import spock.lang.Specification

import java.time.Duration

/**
 * Wires {@link JaiClawSessionRedisAutoConfiguration} into a plain
 * {@link AnnotationConfigApplicationContext} to verify the two
 * conditional annotations (@ConditionalOnClass, @ConditionalOnProperty)
 * behave as expected. Bypasses Spring Boot's
 * {@code ApplicationContextRunner} because Groovy 5 fails to dispatch
 * on that class's SELF-typed varargs builder methods (repro:
 * {@code withUserConfiguration(Class[])} + {@code withPropertyValues(String[])}
 * both throw MissingMethodException from Groovy).
 */
class JaiClawSessionRedisAutoConfigurationSpec extends Specification {

    private AnnotationConfigApplicationContext newContext(Map<String, String> props) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()
        for (Map.Entry<String, String> e : props.entrySet()) {
            ctx.environment.systemProperties.put(e.key, e.value)
        }
        return ctx
    }

    private AnnotationConfigApplicationContext ctxWith(Map<String, Object> env) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()
        if (env != null && !env.isEmpty()) {
            ctx.environment.propertySources.addFirst(new MapPropertySource("test", env))
        }
        // Register the Boot @ConfigurationProperties binder — not present
        // by default in a bare AnnotationConfigApplicationContext.
        ConfigurationPropertiesBindingPostProcessor.register(ctx)
        return ctx
    }

    def "activates and registers RedisSessionManager when backend=redis"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith(
                ["jaiclaw.agent.session.backend": "redis"] as Map<String, Object>)
        ctx.register(TestRedisConfig)
        ctx.register(JaiClawSessionRedisAutoConfiguration)

        when:
        ctx.refresh()

        then:
        SessionManager bean = ctx.getBean(SessionManager)
        bean instanceof RedisSessionManager
        RedisSessionProperties props = ctx.getBean(RedisSessionProperties)
        props.prefix() == "jaiclaw:sessions"
        props.ttl() == Duration.ofDays(30)

        cleanup:
        ctx.close()
    }

    def "no-op when property is unset — bean does not register"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith([:] as Map<String, Object>)
        ctx.register(TestRedisConfig)
        ctx.register(JaiClawSessionRedisAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBeanNamesForType(SessionManager).length == 0

        cleanup:
        ctx.close()
    }

    def "no-op when property is set to a non-redis value"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith(
                ["jaiclaw.agent.session.backend": "inmemory"] as Map<String, Object>)
        ctx.register(TestRedisConfig)
        ctx.register(JaiClawSessionRedisAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBeanNamesForType(SessionManager).length == 0

        cleanup:
        ctx.close()
    }

    def "prefix and ttl bind from environment properties"() {
        given:
        AnnotationConfigApplicationContext ctx = ctxWith([
                "jaiclaw.agent.session.backend"      : "redis",
                "jaiclaw.agent.session.redis.prefix" : "my:sess",
                "jaiclaw.agent.session.redis.ttl"    : "PT48H"
        ] as Map<String, Object>)
        ctx.register(TestRedisConfig)
        ctx.register(JaiClawSessionRedisAutoConfiguration)

        when:
        ctx.refresh()

        then:
        RedisSessionProperties props = ctx.getBean(RedisSessionProperties)
        props.prefix() == "my:sess"
        props.ttl() == Duration.ofHours(48)

        cleanup:
        ctx.close()
    }

    def "adopter-supplied SessionManager wins over the redis-backed default"() {
        given:
        SessionManager custom = Mock()
        AnnotationConfigApplicationContext ctx = ctxWith(
                ["jaiclaw.agent.session.backend": "redis"] as Map<String, Object>)
        ctx.registerBean("customSessionManager", SessionManager, { custom })
        ctx.register(TestRedisConfig)
        ctx.register(JaiClawSessionRedisAutoConfiguration)

        when:
        ctx.refresh()

        then:
        ctx.getBean(SessionManager).is(custom)

        cleanup:
        ctx.close()
    }

    @Configuration
    static class TestRedisConfig {
        @Bean
        LettuceConnectionFactory lettuceConnectionFactory() {
            // Never actually connects — the autoconfig only reads the class + template bean.
            return new LettuceConnectionFactory()
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory f) {
            return new StringRedisTemplate(f)
        }
    }
}
