package io.jaiclaw.messaging.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.jaiclaw.agent.session.SessionManager
import io.jaiclaw.channel.*
import io.jaiclaw.core.model.AssistantMessage
import io.jaiclaw.core.model.Session
import io.jaiclaw.core.model.SessionState
import io.jaiclaw.core.model.UserMessage
import io.jaiclaw.gateway.GatewayService
import io.jaiclaw.messaging.config.MessagingMcpProperties
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.CompletableFuture

class MessagingMcpToolProviderSpec extends Specification {

    ChannelRegistry channelRegistry = Mock()
    GatewayService gatewayService = Mock()
    SessionManager sessionManager = Mock()
    ObjectMapper objectMapper

    MessagingMcpToolProvider provider

    def setup() {
        objectMapper = new ObjectMapper()
        objectMapper.registerModule(new JavaTimeModule())
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private MessagingMcpToolProvider createProvider(MessagingMcpProperties props = null) {
        if (props == null) props = new MessagingMcpProperties(true, List.of(), 50)
        new MessagingMcpToolProvider(channelRegistry, gatewayService, sessionManager, props, objectMapper)
    }

    def "server metadata is correct"() {
        given:
        provider = createProvider()

        expect:
        provider.serverName == "messaging"
        provider.serverDescription.contains("Channel messaging")
    }

    def "getTools returns 8 tools"() {
        given:
        provider = createProvider()

        when:
        def tools = provider.tools

        then:
        tools.size() == 8
        tools.collect { it.name() } as Set == [
            "list_channels", "send_message", "get_channel_status",
            "list_sessions", "get_conversation", "broadcast_message",
            "agent_chat", "agent_chat_async"
        ] as Set
    }

    def "list_channels returns registered adapters"() {
        given:
        provider = createProvider()
        def telegram = mockAdapter("telegram", "Telegram", true, false)
        def slack = mockAdapter("slack", "Slack", false, true)
        channelRegistry.all() >> [telegram, slack]

        when:
        def result = provider.execute("list_channels", [:], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.count == 2
        json.channels[0].channelId == "telegram"
        json.channels[0].running == true
        json.channels[1].channelId == "slack"
        json.channels[1].running == false
    }

    def "send_message success"() {
        given:
        provider = createProvider()
        def adapter = mockAdapter("telegram", "Telegram", true, false)
        channelRegistry.get("telegram") >> Optional.of(adapter)
        adapter.sendMessage(_) >> new DeliveryResult.Success("msg-123")

        when:
        def result = provider.execute("send_message",
                [channelId: "telegram", peerId: "user-1", content: "Hello"], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.success == true
        json.platformMessageId == "msg-123"
    }

    def "send_message to unknown channel returns error"() {
        given:
        provider = createProvider()
        channelRegistry.get("unknown") >> Optional.empty()

        when:
        def result = provider.execute("send_message",
                [channelId: "unknown", peerId: "user-1", content: "Hello"], null)

        then:
        result.isError()
        result.content().contains("Unknown channel")
    }

    def "send_message delivery failure returns error"() {
        given:
        provider = createProvider()
        def adapter = mockAdapter("telegram", "Telegram", true, false)
        channelRegistry.get("telegram") >> Optional.of(adapter)
        adapter.sendMessage(_) >> new DeliveryResult.Failure("RATE_LIMIT", "Too many requests", true)

        when:
        def result = provider.execute("send_message",
                [channelId: "telegram", peerId: "user-1", content: "Hello"], null)

        then:
        result.isError()
        result.content().contains("RATE_LIMIT")
        result.content().contains("Too many requests")
    }

    def "send_message enforces allowedChannels whitelist"() {
        given:
        provider = createProvider(new MessagingMcpProperties(true, ["slack", "email"], 50))

        when:
        def result = provider.execute("send_message",
                [channelId: "telegram", peerId: "user-1", content: "Hello"], null)

        then:
        result.isError()
        result.content().contains("not in the allowed channels list")
    }

    def "get_channel_status for running adapter"() {
        given:
        provider = createProvider()
        def adapter = mockAdapter("telegram", "Telegram", true, false)
        channelRegistry.get("telegram") >> Optional.of(adapter)

        when:
        def result = provider.execute("get_channel_status", [channelId: "telegram"], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.running == true
        json.displayName == "Telegram"
    }

    def "get_channel_status for stopped adapter"() {
        given:
        provider = createProvider()
        def adapter = mockAdapter("slack", "Slack", false, true)
        channelRegistry.get("slack") >> Optional.of(adapter)

        when:
        def result = provider.execute("get_channel_status", [channelId: "slack"], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.running == false
    }

    def "get_channel_status for unknown channel"() {
        given:
        provider = createProvider()
        channelRegistry.get("unknown") >> Optional.empty()

        when:
        def result = provider.execute("get_channel_status", [channelId: "unknown"], null)

        then:
        result.isError()
        result.content().contains("Unknown channel")
    }

    def "list_sessions returns sessions"() {
        given:
        provider = createProvider()
        def session = Session.create("id-1", "key-1", "agent-1")
        sessionManager.listSessions() >> [session]

        when:
        def result = provider.execute("list_sessions", [:], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.count == 1
        json.sessions[0].sessionKey == "key-1"
    }

    def "list_sessions with activeOnly filter"() {
        given:
        provider = createProvider()
        def session = Session.create("id-1", "key-1", "agent-1")
        sessionManager.listActiveSessions() >> [session]

        when:
        def result = provider.execute("list_sessions", [activeOnly: "true"], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.count == 1
    }

    def "get_conversation returns messages"() {
        given:
        provider = createProvider()
        def userMsg = new UserMessage("m1", Instant.now(), "Hello", "sender-1", Map.of())
        def assistantMsg = new AssistantMessage("m2", Instant.now(), "Hi there", "claude-3", null, Map.of())
        def session = Session.create("id-1", "key-1", "agent-1")
                .withMessage(userMsg)
                .withMessage(assistantMsg)
        sessionManager.get("key-1") >> Optional.of(session)

        when:
        def result = provider.execute("get_conversation", [sessionKey: "key-1"], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.count == 2
        json.messages[0].role == "user"
        json.messages[1].role == "assistant"
    }

    def "get_conversation with missing session"() {
        given:
        provider = createProvider()
        sessionManager.get("nonexistent") >> Optional.empty()

        when:
        def result = provider.execute("get_conversation", [sessionKey: "nonexistent"], null)

        then:
        result.isError()
        result.content().contains("Session not found")
    }

    def "get_conversation respects limit"() {
        given:
        provider = createProvider()
        def session = Session.create("id-1", "key-1", "agent-1")
        5.times { i ->
            session = session.withMessage(new UserMessage("m$i", Instant.now(), "msg $i", "sender", Map.of()))
        }
        sessionManager.get("key-1") >> Optional.of(session)

        when:
        def result = provider.execute("get_conversation", [sessionKey: "key-1", limit: 2], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.count == 2
        json.totalMessages == 5
    }

    def "broadcast_message with mixed results"() {
        given:
        provider = createProvider()
        def telegramAdapter = mockAdapter("telegram", "Telegram", true, false)
        def slackAdapter = mockAdapter("slack", "Slack", true, false)
        channelRegistry.get("telegram") >> Optional.of(telegramAdapter)
        channelRegistry.get("slack") >> Optional.of(slackAdapter)
        telegramAdapter.sendMessage(_) >> new DeliveryResult.Success("t-1")
        slackAdapter.sendMessage(_) >> new DeliveryResult.Failure("ERR", "Slack error", false)

        when:
        def result = provider.execute("broadcast_message", [
            recipients: [
                [channelId: "telegram", peerId: "user-1"],
                [channelId: "slack", peerId: "user-2"]
            ],
            content: "Broadcast test"
        ], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.totalRecipients == 2
        json.successCount == 1
        json.failureCount == 1
    }

    def "broadcast_message enforces maxRecipientsPerBroadcast"() {
        given:
        provider = createProvider(new MessagingMcpProperties(true, List.of(), 2))

        when:
        def result = provider.execute("broadcast_message", [
            recipients: [
                [channelId: "a", peerId: "1"],
                [channelId: "b", peerId: "2"],
                [channelId: "c", peerId: "3"]
            ],
            content: "Too many"
        ], null)

        then:
        result.isError()
        result.content().contains("Too many recipients")
        result.content().contains("exceeds maximum of 2")
    }

    def "agent_chat returns synchronous response"() {
        given:
        provider = createProvider()
        def response = new AssistantMessage("r1", Instant.now(), "Agent response", "claude-3", null, Map.of())
        gatewayService.handleSync("mcp", "mcp", "mcp-client", "Hello agent") >> response

        when:
        def result = provider.execute("agent_chat", [content: "Hello agent"], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.response == "Agent response"
    }

    def "agent_chat with channel delivery"() {
        given:
        provider = createProvider()
        def response = new AssistantMessage("r1", Instant.now(), "Agent response", "claude-3", null, Map.of())
        def adapter = mockAdapter("telegram", "Telegram", true, false)
        gatewayService.handleSync("telegram", "mcp", "user-1", "Hello") >> response
        channelRegistry.get("telegram") >> Optional.of(adapter)
        adapter.sendMessage(_) >> new DeliveryResult.Success("out-1")

        when:
        def result = provider.execute("agent_chat",
                [content: "Hello", channelId: "telegram", peerId: "user-1", deliverToChannel: "true"], null)

        then:
        !result.isError()
        1 * adapter.sendMessage(_)
    }

    def "agent_chat_async returns acceptance immediately"() {
        given:
        provider = createProvider()
        gatewayService.handleAsync(_, _) >> CompletableFuture.completedFuture(
                new AssistantMessage("r1", Instant.now(), "Response", "claude-3", null, Map.of()))
        channelRegistry.get("telegram") >> Optional.of(mockAdapter("telegram", "Telegram", true, false))

        when:
        def result = provider.execute("agent_chat_async",
                [content: "Async hello", channelId: "telegram", peerId: "user-1"], null)

        then:
        !result.isError()
        def json = objectMapper.readValue(result.content(), Map)
        json.accepted == true
        json.channelId == "telegram"
    }

    def "agent_chat_async enforces allowedChannels"() {
        given:
        provider = createProvider(new MessagingMcpProperties(true, ["email"], 50))

        when:
        def result = provider.execute("agent_chat_async",
                [content: "Hello", channelId: "telegram", peerId: "user-1"], null)

        then:
        result.isError()
        result.content().contains("not in the allowed channels list")
    }

    def "unknown tool returns error"() {
        given:
        provider = createProvider()

        when:
        def result = provider.execute("unknown_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "missing required params returns error"() {
        given:
        provider = createProvider()

        when:
        def result = provider.execute("send_message", [channelId: "telegram"], null)

        then:
        result.isError()
        result.content().contains("Missing required parameter")
    }

    // ── helpers ──

    private ChannelAdapter mockAdapter(String id, String name, boolean running, boolean streaming) {
        def adapter = Mock(ChannelAdapter)
        adapter.channelId() >> id
        adapter.displayName() >> name
        adapter.isRunning() >> running
        adapter.supportsStreaming() >> streaming
        return adapter
    }
}
