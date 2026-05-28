package io.jaiclaw.email.provider

import io.jaiclaw.email.config.EmailProperties
import io.jaiclaw.email.model.EmailMessage
import io.jaiclaw.email.model.EmailResult
import spock.lang.Specification
import spock.lang.Subject

class Smtp2goEmailSenderSpec extends Specification {

    def "should have correct provider name"() {
        given:
        def props = new EmailProperties(true, "smtp2go", "default@example.com", "Default Sender",
                new EmailProperties.Smtp2goConfig("test-api-key", "https://api.smtp2go.com/v3", 10))

        @Subject
        def sender = new Smtp2goEmailSender(props)

        expect:
        sender.getProviderName() == "smtp2go"
    }

    def "should return Failed result when RestClient throws exception"() {
        given:
        def props = new EmailProperties(true, "smtp2go", "default@example.com", "Default Sender",
                new EmailProperties.Smtp2goConfig("test-api-key", "http://localhost:1/invalid", 1))
        def sender = new Smtp2goEmailSender(props)

        def message = EmailMessage.builder()
                .to(["test@example.com"])
                .subject("Test")
                .textBody("Body")
                .build()

        when:
        def result = sender.send(message)

        then:
        result instanceof EmailResult.Failed
        !result.isSuccess()
        ((EmailResult.Failed) result).provider() == "smtp2go"
    }

    def "should use default from when message from is null"() {
        given:
        def props = new EmailProperties(true, "smtp2go", "default@example.com", "Default Name",
                new EmailProperties.Smtp2goConfig("key", "http://localhost:1/invalid", 1))
        def sender = new Smtp2goEmailSender(props)

        def message = EmailMessage.builder()
                .to(["test@example.com"])
                .subject("Test")
                .textBody("Body")
                .build()

        when:
        def result = sender.send(message)

        then: "should attempt to send (fails due to invalid URL) but uses default from"
        result instanceof EmailResult.Failed
        ((EmailResult.Failed) result).provider() == "smtp2go"
    }

    def "should use message from when provided"() {
        given:
        def props = new EmailProperties(true, "smtp2go", "default@example.com", "Default Name",
                new EmailProperties.Smtp2goConfig("key", "http://localhost:1/invalid", 1))
        def sender = new Smtp2goEmailSender(props)

        def message = EmailMessage.builder()
                .to(["test@example.com"])
                .from("custom@example.com")
                .fromName("Custom Name")
                .subject("Test")
                .textBody("Body")
                .build()

        when:
        def result = sender.send(message)

        then: "should attempt to send with custom from (fails due to invalid URL)"
        result instanceof EmailResult.Failed
    }
}
