package io.jaiclaw.memory

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import spock.lang.Specification

import java.time.Duration

class NativeResilience4jCircuitBreakerSpec extends Specification {

    def "executes supplier on success"() {
        given:
        def cb = new NativeResilience4jCircuitBreaker()

        when:
        def result = cb.run({ "hello" }, { ex -> "fallback" })

        then:
        result == "hello"
    }

    def "invokes fallback on failure"() {
        given:
        def cb = new NativeResilience4jCircuitBreaker()

        when:
        def result = cb.run({ throw new RuntimeException("boom") }, { ex -> "fallback: " + ex.message })

        then:
        result == "fallback: boom"
    }
}
