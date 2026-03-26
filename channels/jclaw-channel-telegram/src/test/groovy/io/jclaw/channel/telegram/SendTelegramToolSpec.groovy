package io.jclaw.channel.telegram

import io.jclaw.channel.ChannelAdapter
import io.jclaw.channel.ChannelMessage
import io.jclaw.channel.ChannelRegistry
import io.jclaw.channel.DeliveryResult
import io.jclaw.core.tool.ToolContext
import io.jclaw.core.tool.ToolProfile
import io.jclaw.core.tool.ToolResult
import spock.lang.Specification
import spock.lang.Subject

class SendTelegramToolSpec extends Specification {

    ChannelRegistry channelRegistry = Mock()
    ToolContext ctx = new ToolContext("agent-1", "key", "session-1", "/tmp")

    @Subject
    SendTelegramTool tool = new SendTelegramTool(channelRegistry)

    def "definition has correct name and profiles"() {
        when:
        def defn = tool.definition()

        then:
        defn.name() == "send_telegram"
        defn.profiles().contains(ToolProfile.FULL)
        defn.profiles().contains(ToolProfile.MINIMAL)
    }

    def "returns error when chat_id is missing"() {
        when:
        def result = tool.execute([message: "hello"], ctx)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message() == "chat_id is required"
    }

    def "returns error when message is missing"() {
        when:
        def result = tool.execute([chat_id: "123"], ctx)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message() == "message is required"
    }

    def "returns error when telegram adapter is not available"() {
        given:
        channelRegistry.get("telegram") >> Optional.empty()

        when:
        def result = tool.execute([chat_id: "123", message: "hello"], ctx)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Telegram channel adapter not available")
    }

    def "sends message successfully via telegram adapter"() {
        given:
        def adapter = Mock(ChannelAdapter)
        channelRegistry.get("telegram") >> Optional.of(adapter)
        adapter.sendMessage(_ as ChannelMessage) >> new DeliveryResult.Success("msg-1")

        when:
        def result = tool.execute([chat_id: "123", message: "health report"], ctx)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("Message sent to Telegram chat 123")
    }

    def "returns error on delivery failure"() {
        given:
        def adapter = Mock(ChannelAdapter)
        channelRegistry.get("telegram") >> Optional.of(adapter)
        adapter.sendMessage(_ as ChannelMessage) >> new DeliveryResult.Failure("SEND_FAIL", "timeout", true)

        when:
        def result = tool.execute([chat_id: "123", message: "report"], ctx)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Failed to send: timeout")
    }
}
