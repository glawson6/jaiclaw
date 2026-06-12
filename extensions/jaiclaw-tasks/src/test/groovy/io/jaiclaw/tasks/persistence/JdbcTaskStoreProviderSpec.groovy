package io.jaiclaw.tasks.persistence

import io.jaiclaw.tasks.persistence.h2.H2TaskStore
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class JdbcTaskStoreProviderSpec extends Specification {

    static final AtomicInteger DB_SEQ = new AtomicInteger(0)

    JdbcTaskStoreProvider provider = new JdbcTaskStoreProvider()

    def "supports jdbc and h2 type names"() {
        expect:
        provider.supports("jdbc")
        provider.supports("JDBC")
        provider.supports("h2")
        provider.supports("H2")
        !provider.supports("redis")
        !provider.supports("json")
    }

    def "create builds an H2TaskStore from url config"() {
        given:
        String url = "jdbc:h2:mem:provider-${DB_SEQ.incrementAndGet()};INIT=CREATE TABLE IF NOT EXISTS jaiclaw_tasks (id VARCHAR PRIMARY KEY, tenant_id VARCHAR);DB_CLOSE_DELAY=-1"

        when:
        def store = provider.create(null, [url: url, user: "sa", password: ""])

        then:
        store instanceof H2TaskStore
    }

    def "create rejects missing url"() {
        when:
        provider.create(null, [:])

        then:
        thrown(IllegalArgumentException)
    }
}
