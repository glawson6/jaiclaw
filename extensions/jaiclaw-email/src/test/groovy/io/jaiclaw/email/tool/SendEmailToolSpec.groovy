package io.jaiclaw.email.tool

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.email.config.EmailProperties
import io.jaiclaw.email.model.EmailMessage
import io.jaiclaw.email.model.EmailResult
import io.jaiclaw.email.provider.EmailSender
import spock.lang.Specification
import spock.lang.Subject

class SendEmailToolSpec extends Specification {

    EmailSender emailSender = Mock()
    EmailProperties properties = new EmailProperties(true, "smtp2go", "default@example.com", "Default",
            new EmailProperties.Smtp2goConfig("key", "https://api.smtp2go.com/v3", 10))

    @Subject
    SendEmailTool tool = new SendEmailTool(emailSender, properties)

    def ctx = new ToolContext(null, null, null, null)

    def "should have correct tool definition"() {
        expect:
        tool.definition().name() == "email_send"
        tool.definition().section() == "Email"
        tool.definition().inputSchema().contains("to")
        tool.definition().inputSchema().contains("subject")
    }

    def "should send email successfully"() {
        given:
        def params = [
                to: ["alice@example.com"],
                subject: "Hello",
                textBody: "World"
        ]

        when:
        def result = tool.execute(params, ctx)

        then:
        1 * emailSender.send(_ as EmailMessage) >> new EmailResult.Sent("msg-123", 1, "smtp2go")
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains('"status":"sent"')
        ((ToolResult.Success) result).content().contains('"messageId":"msg-123"')
    }

    def "should return failure result when send fails"() {
        given:
        def params = [
                to: ["alice@example.com"],
                subject: "Hello",
                textBody: "World"
        ]

        when:
        def result = tool.execute(params, ctx)

        then:
        1 * emailSender.send(_ as EmailMessage) >> new EmailResult.Failed("API error", "ERR_001", "smtp2go")
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains('"status":"failed"')
        ((ToolResult.Success) result).content().contains('"error":"API error"')
    }

    def "should return error when 'to' is missing"() {
        given:
        def params = [
                subject: "Hello",
                textBody: "World"
        ]

        when:
        def result = tool.execute(params, ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Missing required parameter: to")
    }

    def "should return error when 'subject' is missing"() {
        given:
        def params = [
                to: ["alice@example.com"],
                textBody: "World"
        ]

        when:
        def result = tool.execute(params, ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("Missing required parameter: subject")
    }

    def "should return error when neither htmlBody nor textBody is provided"() {
        given:
        def params = [
                to: ["alice@example.com"],
                subject: "Hello"
        ]

        when:
        def result = tool.execute(params, ctx)

        then:
        result instanceof ToolResult.Error
        ((ToolResult.Error) result).message().contains("At least one of htmlBody or textBody must be provided")
    }

    def "should pass optional fields to EmailMessage"() {
        given:
        def params = [
                to: ["alice@example.com"],
                cc: ["cc@example.com"],
                bcc: ["bcc@example.com"],
                from: "custom@example.com",
                fromName: "Custom Sender",
                replyTo: "reply@example.com",
                subject: "Hello",
                htmlBody: "<p>HTML</p>",
                textBody: "Text"
        ]

        when:
        tool.execute(params, ctx)

        then:
        1 * emailSender.send({ EmailMessage msg ->
            msg.to() == ["alice@example.com"] &&
            msg.cc() == ["cc@example.com"] &&
            msg.bcc() == ["bcc@example.com"] &&
            msg.from() == "custom@example.com" &&
            msg.fromName() == "Custom Sender" &&
            msg.replyTo() == "reply@example.com" &&
            msg.subject() == "Hello" &&
            msg.htmlBody() == "<p>HTML</p>" &&
            msg.textBody() == "Text"
        }) >> new EmailResult.Sent("id", 1, "smtp2go")
    }
}
