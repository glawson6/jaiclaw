package io.jaiclaw.email.model

import spock.lang.Specification

class EmailMessageSpec extends Specification {

    def "should build message with all fields"() {
        when:
        def message = EmailMessage.builder()
                .to(["alice@example.com", "bob@example.com"])
                .cc(["cc@example.com"])
                .bcc(["bcc@example.com"])
                .from("sender@example.com")
                .fromName("Sender Name")
                .replyTo("reply@example.com")
                .subject("Test Subject")
                .textBody("Hello, World!")
                .htmlBody("<p>Hello, World!</p>")
                .metadata([key: "value"])
                .build()

        then:
        message.to() == ["alice@example.com", "bob@example.com"]
        message.cc() == ["cc@example.com"]
        message.bcc() == ["bcc@example.com"]
        message.from() == "sender@example.com"
        message.fromName() == "Sender Name"
        message.replyTo() == "reply@example.com"
        message.subject() == "Test Subject"
        message.textBody() == "Hello, World!"
        message.htmlBody() == "<p>Hello, World!</p>"
        message.metadata() == [key: "value"]
    }

    def "should default null lists to empty"() {
        when:
        def message = EmailMessage.builder()
                .subject("Test")
                .textBody("Body")
                .build()

        then:
        message.to() == []
        message.cc() == []
        message.bcc() == []
        message.metadata() == [:]
    }

    def "should make lists immutable"() {
        given:
        def toList = new ArrayList(["a@example.com"])

        when:
        def message = EmailMessage.builder()
                .to(toList)
                .subject("Test")
                .textBody("Body")
                .build()

        then:
        message.to() == ["a@example.com"]

        when:
        message.to().add("b@example.com")

        then:
        thrown(UnsupportedOperationException)
    }

    def "should allow null optional fields"() {
        when:
        def message = EmailMessage.builder()
                .to(["test@example.com"])
                .subject("Test")
                .build()

        then:
        message.from() == null
        message.fromName() == null
        message.replyTo() == null
        message.textBody() == null
        message.htmlBody() == null
    }
}
