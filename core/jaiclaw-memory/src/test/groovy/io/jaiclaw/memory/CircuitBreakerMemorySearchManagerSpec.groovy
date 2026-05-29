package io.jaiclaw.memory

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import spock.lang.Specification

import java.time.Duration

class CircuitBreakerMemorySearchManagerSpec extends Specification {

    def toggleStore = new InMemoryToggleStore()

    def "delegates to underlying search manager"() {
        given:
        def expected = [new MemorySearchResult("path", 1, 10, 0.9, "snippet", MemorySource.WORKSPACE)]
        def delegate = Mock(MemorySearchManager)
        def manager = new CircuitBreakerMemorySearchManager(delegate, toggleStore)
        def options = new MemorySearchOptions(5, 0.5, "s1")

        when:
        def results = manager.search("test", options)

        then:
        1 * delegate.search("test", options) >> expected
        results == expected
    }

    def "returns empty list when session is disabled"() {
        given:
        def delegate = Mock(MemorySearchManager)
        def manager = new CircuitBreakerMemorySearchManager(delegate, toggleStore)
        toggleStore.disable("s1")

        when:
        def results = manager.search("test", new MemorySearchOptions(5, 0.5, "s1"))

        then:
        0 * delegate._
        results.isEmpty()
    }

    def "returns empty list when circuit breaker is open"() {
        given:
        def delegate = Mock(MemorySearchManager)
        def cb = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build())
        def manager = new CircuitBreakerMemorySearchManager(delegate, toggleStore, cb)

        // Force circuit open by failing enough calls
        delegate.search(_, _) >> { throw new RuntimeException("fail") }
        manager.search("q1", MemorySearchOptions.DEFAULT)
        manager.search("q2", MemorySearchOptions.DEFAULT)

        when:
        def results = manager.search("q3", MemorySearchOptions.DEFAULT)

        then:
        results.isEmpty()
        manager.circuitBreakerState() == CircuitBreaker.State.OPEN
    }

    def "works when session key is null"() {
        given:
        def delegate = Mock(MemorySearchManager)
        def manager = new CircuitBreakerMemorySearchManager(delegate, toggleStore)
        def options = new MemorySearchOptions(5, 0.5, null)

        when:
        def results = manager.search("test", options)

        then:
        1 * delegate.search("test", options) >> []
        results.isEmpty()
    }
}
