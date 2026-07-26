package io.jaiclaw.channel.googlechat

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.channel.DeliveryResult
import io.jaiclaw.channel.chunking.PlatformLimits
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpResponse

class GoogleChatAdapterSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    HttpClient mockHttpClient = Mock(HttpClient)

    GoogleChatConfig config = new GoogleChatConfig(
            "test-project", "/path/to/key.json", "/webhooks/googlechat", true, Set.of()
    )

    GoogleChatAdapter adapter = new GoogleChatAdapter(config, mockHttpClient)

    def "channelId is googlechat"() {
        expect:
        adapter.channelId() == "googlechat"
        adapter.displayName() == "Google Chat"
    }

    def "platformLimits returns Google Chat limits"() {
        expect:
        adapter.platformLimits() == PlatformLimits.GOOGLE_CHAT
        adapter.platformLimits().maxTextLength() == 4096
    }

    def "start sets running state"() {
        given:
        def handler = Mock(ChannelMessageHandler)

        when:
        adapter.start(handler)

        then:
        adapter.isRunning()

        cleanup:
        adapter.stop()
    }

    def "stop clears running state"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))

        when:
        adapter.stop()

        then:
        !adapter.isRunning()
    }

    def "processEvent dispatches MESSAGE event to handler"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def event = MAPPER.readTree('''{
            "type": "MESSAGE",
            "message": {
                "name": "spaces/SPACE1/messages/MSG1",
                "text": "hello from Google Chat",
                "thread": {"name": "spaces/SPACE1/threads/THREAD1"}
            },
            "space": {"name": "spaces/SPACE1"},
            "user": {"name": "users/12345", "displayName": "Test User"}
        }''')

        when:
        adapter.processEvent(event)

        then:
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.channelId() == "googlechat" &&
            msg.peerId() == "users/12345" &&
            msg.accountId() == "spaces/SPACE1" &&
            msg.content() == "hello from Google Chat" &&
            msg.direction() == ChannelMessage.Direction.INBOUND
        })

        cleanup:
        adapter.stop()
    }

    def "processEvent ignores non-MESSAGE events"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def event = MAPPER.readTree('''{
            "type": "ADDED_TO_SPACE",
            "space": {"name": "spaces/SPACE1"},
            "user": {"name": "users/12345"}
        }''')

        when:
        adapter.processEvent(event)

        then:
        0 * handler.onMessage(_)

        cleanup:
        adapter.stop()
    }

    def "processEvent ignores events without sender"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def event = MAPPER.readTree('''{
            "type": "MESSAGE",
            "message": {"text": "hello"},
            "space": {"name": "spaces/SPACE1"},
            "user": {}
        }''')

        when:
        adapter.processEvent(event)

        then:
        0 * handler.onMessage(_)

        cleanup:
        adapter.stop()
    }

    def "allowed-sender filter drops unauthorized messages"() {
        given:
        def restrictedConfig = new GoogleChatConfig(
                "test-project", "/path/to/key.json", "/webhooks/googlechat", true,
                Set.of("users/00001")
        )
        def restrictedAdapter = new GoogleChatAdapter(restrictedConfig, mockHttpClient)
        def handler = Mock(ChannelMessageHandler)
        restrictedAdapter.start(handler)

        def event = MAPPER.readTree('''{
            "type": "MESSAGE",
            "message": {"name": "msg1", "text": "blocked"},
            "space": {"name": "spaces/SPACE1"},
            "user": {"name": "users/99999", "displayName": "Blocked User"}
        }''')

        when:
        restrictedAdapter.processEvent(event)

        then:
        0 * handler.onMessage(_)

        cleanup:
        restrictedAdapter.stop()
    }

    def "sendMessage posts to Chat API and returns success"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "googlechat", "spaces/SPACE1", "users/12345", "hello")

        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> '{"name": "spaces/SPACE1/messages/NEWMSG"}'
        mockHttpClient.send(_, _) >> mockResponse

        when:
        def result = adapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Success
        (result as DeliveryResult.Success).platformMessageId() == "spaces/SPACE1/messages/NEWMSG"

        cleanup:
        adapter.stop()
    }

    def "sendMessage returns failure on HTTP error"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "googlechat", "spaces/SPACE1", "users/12345", "hello")

        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 403
        mockHttpClient.send(_, _) >> mockResponse

        when:
        def result = adapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Failure
        (result as DeliveryResult.Failure).errorCode() == "googlechat_api_error"

        cleanup:
        adapter.stop()
    }

    def "sendMessage returns failure on exception"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "googlechat", "spaces/SPACE1", "users/12345", "hello")

        mockHttpClient.send(_, _) >> { throw new IOException("connection refused") }

        when:
        def result = adapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Failure
        (result as DeliveryResult.Failure).message() == "connection refused"

        cleanup:
        adapter.stop()
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new GoogleChatConfig(null, null, null, false, null)

        then:
        cfg.projectId() == ""
        cfg.serviceAccountKeyPath() == ""
        cfg.webhookPath() == "/webhooks/googlechat"
        cfg.allowedSenderIds() == Set.of()
    }

    def "config isSenderAllowed with empty set allows all"() {
        expect:
        config.isSenderAllowed("users/12345")
        config.isSenderAllowed("users/99999")
    }

    def "config isSenderAllowed with non-empty set filters"() {
        given:
        def cfg = new GoogleChatConfig("p", "k", "/wh", true, Set.of("users/00001"))

        expect:
        cfg.isSenderAllowed("users/00001")
        !cfg.isSenderAllowed("users/99999")
    }

    def "DISABLED config has expected defaults"() {
        expect:
        !GoogleChatConfig.DISABLED.enabled()
        GoogleChatConfig.DISABLED.webhookPath() == "/webhooks/googlechat"
    }
}
