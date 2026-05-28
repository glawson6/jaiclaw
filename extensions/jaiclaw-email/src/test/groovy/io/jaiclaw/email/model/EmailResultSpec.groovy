package io.jaiclaw.email.model

import spock.lang.Specification

class EmailResultSpec extends Specification {

    def "Sent result should report success"() {
        when:
        def result = new EmailResult.Sent("msg-123", 3, "smtp2go")

        then:
        result.isSuccess()
        result.messageId() == "msg-123"
        result.recipientCount() == 3
        result.provider() == "smtp2go"
    }

    def "Failed result should report failure"() {
        when:
        def result = new EmailResult.Failed("Connection timeout", "TIMEOUT", "smtp2go")

        then:
        !result.isSuccess()
        result.error() == "Connection timeout"
        result.errorCode() == "TIMEOUT"
        result.provider() == "smtp2go"
    }

    def "Failed result should allow null errorCode via convenience constructor"() {
        when:
        def result = new EmailResult.Failed("Something went wrong", "smtp2go")

        then:
        !result.isSuccess()
        result.error() == "Something went wrong"
        result.errorCode() == null
        result.provider() == "smtp2go"
    }

    def "should be instances of sealed EmailResult"() {
        expect:
        new EmailResult.Sent("id", 1, "p") instanceof EmailResult
        new EmailResult.Failed("err", "p") instanceof EmailResult
    }
}
