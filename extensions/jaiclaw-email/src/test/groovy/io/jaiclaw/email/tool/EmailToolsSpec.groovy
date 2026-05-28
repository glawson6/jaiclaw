package io.jaiclaw.email.tool

import io.jaiclaw.email.config.EmailProperties
import io.jaiclaw.email.provider.EmailSender
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Specification

class EmailToolsSpec extends Specification {

    EmailSender emailSender = Mock()
    EmailProperties properties = new EmailProperties(true, "smtp2go", "test@example.com", "Test",
            new EmailProperties.Smtp2goConfig("key", "https://api.smtp2go.com/v3", 10))

    def "should create all email tools"() {
        when:
        def tools = EmailTools.all(emailSender, properties)

        then:
        tools.size() == 1
        tools.find { it.definition().name() == "email_send" } != null
    }

    def "should register all tools with registry"() {
        given:
        def registry = Mock(ToolRegistry)

        when:
        EmailTools.registerAll(registry, emailSender, properties)

        then:
        1 * registry.registerAll({ it.size() == 1 })
    }

    def "all tools should be in Email section"() {
        when:
        def tools = EmailTools.all(emailSender, properties)

        then:
        tools.every { it.definition().section() == "Email" }
    }
}
