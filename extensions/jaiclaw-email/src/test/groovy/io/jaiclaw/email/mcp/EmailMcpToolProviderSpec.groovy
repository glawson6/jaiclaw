package io.jaiclaw.email.mcp

import io.jaiclaw.email.config.EmailProperties
import io.jaiclaw.email.model.EmailMessage
import io.jaiclaw.email.model.EmailResult
import io.jaiclaw.email.provider.EmailSender
import spock.lang.Specification
import spock.lang.Subject

class EmailMcpToolProviderSpec extends Specification {

    EmailSender emailSender = Mock()
    EmailProperties properties = new EmailProperties(true, "smtp2go", "default@example.com", "Default",
            new EmailProperties.Smtp2goConfig("key", "https://api.smtp2go.com/v3", 10))

    @Subject
    EmailMcpToolProvider provider = new EmailMcpToolProvider(emailSender, properties)

    def "should have correct server name"() {
        expect:
        provider.getServerName() == "email"
    }

    def "should have correct server description"() {
        expect:
        provider.getServerDescription().contains("Email")
    }

    def "should expose send_email tool"() {
        when:
        def tools = provider.getTools()

        then:
        tools.size() == 1
        tools[0].name() == "send_email"
        tools[0].inputSchema().contains("to")
    }

    def "should send email successfully via MCP"() {
        given:
        def args = [
                to: ["alice@example.com"],
                subject: "Hello",
                textBody: "World"
        ]

        when:
        def result = provider.execute("send_email", args, null)

        then:
        1 * emailSender.send(_ as EmailMessage) >> new EmailResult.Sent("msg-123", 1, "smtp2go")
        !result.isError()
        result.content().contains("sent")
    }

    def "should return error for missing 'to'"() {
        given:
        def args = [
                subject: "Hello",
                textBody: "World"
        ]

        when:
        def result = provider.execute("send_email", args, null)

        then:
        result.isError()
        result.content().contains("Missing required parameter: to")
    }

    def "should return error for unknown tool"() {
        when:
        def result = provider.execute("unknown_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "should return error when neither body is provided"() {
        given:
        def args = [
                to: ["alice@example.com"],
                subject: "Hello"
        ]

        when:
        def result = provider.execute("send_email", args, null)

        then:
        result.isError()
        result.content().contains("At least one of htmlBody or textBody must be provided")
    }
}
