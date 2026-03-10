package io.jclaw.channel

import spock.lang.Specification

class DeliveryResultSpec extends Specification {

    def "Success carries platform message ID"() {
        when:
        def result = new DeliveryResult.Success("msg_123")

        then:
        result instanceof DeliveryResult
        result.platformMessageId() == "msg_123"
        result.platformData().isEmpty()
    }

    def "Success with platform data"() {
        given:
        def data = Map.of("ts", "1234567890.123456")

        when:
        def result = new DeliveryResult.Success("msg_123", data)

        then:
        result.platformData() == data
    }

    def "Failure carries error details"() {
        when:
        def result = new DeliveryResult.Failure("rate_limited", "Too many requests", true)

        then:
        result instanceof DeliveryResult
        result.errorCode() == "rate_limited"
        result.message() == "Too many requests"
        result.retryable()
    }

    def "pattern matching on sealed interface"() {
        given:
        DeliveryResult success = new DeliveryResult.Success("id1")
        DeliveryResult failure = new DeliveryResult.Failure("err", "msg", false)

        expect:
        success instanceof DeliveryResult.Success
        failure instanceof DeliveryResult.Failure
    }
}
