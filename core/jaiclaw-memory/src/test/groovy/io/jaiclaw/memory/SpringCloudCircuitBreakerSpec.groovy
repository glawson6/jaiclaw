package io.jaiclaw.memory

import spock.lang.Specification

class SpringCloudCircuitBreakerSpec extends Specification {

    def "delegates to Spring Cloud CircuitBreaker on success"() {
        given:
        def springCb = Mock(org.springframework.cloud.client.circuitbreaker.CircuitBreaker)
        def cb = new SpringCloudCircuitBreaker(springCb)

        when:
        def result = cb.run({ "hello" }, { ex -> "fallback" })

        then:
        1 * springCb.run(_, _) >> { args -> args[0].get() }
        result == "hello"
    }

    def "delegates to Spring Cloud CircuitBreaker on failure"() {
        given:
        def springCb = Mock(org.springframework.cloud.client.circuitbreaker.CircuitBreaker)
        def cb = new SpringCloudCircuitBreaker(springCb)

        when:
        def result = cb.run({ throw new RuntimeException("boom") }, { ex -> "fallback" })

        then:
        1 * springCb.run(_, _) >> { args -> args[1].apply(new RuntimeException("boom")) }
        result == "fallback"
    }
}
