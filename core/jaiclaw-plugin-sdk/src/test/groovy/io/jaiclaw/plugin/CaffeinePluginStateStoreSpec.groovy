package io.jaiclaw.plugin

import spock.lang.Specification

import java.time.Duration

class CaffeinePluginStateStoreSpec extends Specification {

    def store = new CaffeinePluginStateStore()

    def "put and get returns stored value"() {
        when:
        store.put("key1", "hello")

        then:
        store.get("key1") == "hello"
    }

    def "get with type returns typed value"() {
        when:
        store.put("count", 42)

        then:
        store.get("count", Integer) == 42
    }

    def "get with wrong type throws ClassCastException"() {
        given:
        store.put("key", "string-value")

        when:
        store.get("key", Integer)

        then:
        thrown(ClassCastException)
    }

    def "remove deletes entry from both caches"() {
        given:
        store.put("perm", "permanent")
        store.put("temp", "temporary", Duration.ofMinutes(5))

        when:
        store.remove("perm")
        store.remove("temp")

        then:
        store.get("perm") == null
        store.get("temp") == null
    }

    def "clear removes all entries"() {
        given:
        store.put("a", 1)
        store.put("b", 2)
        store.put("c", 3, Duration.ofMinutes(5))

        when:
        store.clear()

        then:
        store.size() == 0
        store.get("a") == null
    }

    def "size reflects entry count across both caches"() {
        when:
        store.put("perm1", "v1")
        store.put("perm2", "v2")
        store.put("exp1", "v3", Duration.ofMinutes(10))

        then:
        store.size() == 3
    }
}
