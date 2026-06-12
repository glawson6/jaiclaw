package io.jaiclaw.tasks.persistence.redis

import com.redis.testcontainers.RedisContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

class RedisTaskStoreProviderSpec extends Specification {

    @Shared
    RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"))

    def setupSpec() { redis.start() }
    def cleanupSpec() { redis.stop() }

    def provider = new RedisTaskStoreProvider()

    def "supports the redis type only"() {
        expect:
        provider.supports("redis")
        provider.supports("REDIS")
        !provider.supports("jdbc")
        !provider.supports("json")
    }

    def "create builds a RedisTaskStore that round-trips"() {
        when:
        def store = provider.create(null, [host: redis.host,
                                            port: redis.firstMappedPort.toString(),
                                            "key-prefix": "spec:prov"])

        then:
        store instanceof RedisTaskStore
        store.count() == 0L
    }
}
