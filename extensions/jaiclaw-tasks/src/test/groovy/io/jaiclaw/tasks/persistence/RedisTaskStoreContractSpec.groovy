package io.jaiclaw.tasks.persistence

import com.redis.testcontainers.RedisContainer
import io.jaiclaw.tasks.TaskStore
import io.jaiclaw.tasks.persistence.redis.RedisTaskStore
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

/**
 * Plan §9 group 4b — pins {@link RedisTaskStore} against the shared
 * {@link TaskStoreContractSpec}. Uses Testcontainers Redis.
 *
 * <p>Container started once per spec class (Shared). Each test method
 * calls FLUSHDB to start with a clean store.
 */
class RedisTaskStoreContractSpec extends TaskStoreContractSpec {

    @Shared
    RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"))

    @Shared
    StringRedisTemplate template

    @Shared
    JedisConnectionFactory connFactory

    def setupSpec() {
        redis.start()
        connFactory = new JedisConnectionFactory(
                new RedisStandaloneConfiguration(redis.host, redis.firstMappedPort))
        connFactory.afterPropertiesSet()
        template = new StringRedisTemplate(connFactory)
        template.afterPropertiesSet()
    }

    def cleanupSpec() {
        connFactory?.destroy()
        redis.stop()
    }

    @Override
    TaskStore createStore() {
        template.execute({ connection ->
            connection.serverCommands().flushDb()
            return null
        } as org.springframework.data.redis.core.RedisCallback)
        return new RedisTaskStore(template)
    }
}
