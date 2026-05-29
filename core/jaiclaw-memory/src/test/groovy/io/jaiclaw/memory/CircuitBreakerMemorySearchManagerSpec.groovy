package io.jaiclaw.memory

import spock.lang.Specification

class CircuitBreakerMemorySearchManagerSpec extends Specification {

    def toggleStore = new InMemoryToggleStore()

    def "delegates to underlying search manager"() {
        given:
        def expected = [new MemorySearchResult("path", 1, 10, 0.9, "snippet", MemorySource.WORKSPACE)]
        def delegate = Mock(MemorySearchManager)
        def cb = Mock(MemoryCircuitBreaker)
        def manager = new CircuitBreakerMemorySearchManager(delegate, toggleStore, cb)
        def options = new MemorySearchOptions(5, 0.5, "s1")

        when:
        def results = manager.search("test", options)

        then:
        1 * cb.run(_, _) >> { args -> args[0].get() }
        1 * delegate.search("test", options) >> expected
        results == expected
    }

    def "returns empty list when session is disabled"() {
        given:
        def delegate = Mock(MemorySearchManager)
        def cb = Mock(MemoryCircuitBreaker)
        def manager = new CircuitBreakerMemorySearchManager(delegate, toggleStore, cb)
        toggleStore.disable("s1")

        when:
        def results = manager.search("test", new MemorySearchOptions(5, 0.5, "s1"))

        then:
        0 * delegate._
        0 * cb._
        results.isEmpty()
    }

    def "returns fallback when circuit breaker invokes fallback"() {
        given:
        def delegate = Mock(MemorySearchManager)
        def cb = Mock(MemoryCircuitBreaker)
        def manager = new CircuitBreakerMemorySearchManager(delegate, toggleStore, cb)

        when:
        def results = manager.search("test", MemorySearchOptions.DEFAULT)

        then:
        1 * cb.run(_, _) >> { args -> args[1].apply(new RuntimeException("fail")) }
        results.isEmpty()
    }

    def "works when session key is null"() {
        given:
        def delegate = Mock(MemorySearchManager)
        def cb = Mock(MemoryCircuitBreaker)
        def manager = new CircuitBreakerMemorySearchManager(delegate, toggleStore, cb)
        def options = new MemorySearchOptions(5, 0.5, null)

        when:
        def results = manager.search("test", options)

        then:
        1 * cb.run(_, _) >> { args -> args[0].get() }
        1 * delegate.search("test", options) >> []
        results.isEmpty()
    }
}
