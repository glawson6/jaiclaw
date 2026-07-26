package io.jaiclaw.channel.line

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.channel.DeliveryResult
import io.jaiclaw.channel.chunking.PlatformLimits
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpResponse

class LineAdapterSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    HttpClient mockHttpClient = Mock(HttpClient)

    LineConfig config = new LineConfig("test-token", "test-secret", true, Set.of())

    LineAdapter adapter = new LineAdapter(config, mockHttpClient)

    def "channelId is line"() {
        expect:
        adapter.channelId() == "line"
        adapter.displayName() == "LINE"
    }

    def "platformLimits returns LINE limits"() {
        expect:
        adapter.platformLimits() == PlatformLimits.LINE
        adapter.platformLimits().maxTextLength() == 5000
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

    def "processEvent dispatches text message to handler"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def event = MAPPER.readTree('''{
            "type": "message",
            "source": {"type": "user", "userId": "U1234567890"},
            "replyToken": "reply-token-123",
            "message": {"id": "msg-001", "type": "text", "text": "hello from LINE"}
        }''')

        when:
        adapter.processEvent(event)

        then:
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.channelId() == "line" &&
            msg.peerId() == "U1234567890" &&
            msg.content() == "hello from LINE" &&
            msg.direction() == ChannelMessage.Direction.INBOUND &&
            msg.platformData().get("replyToken") == "reply-token-123"
        })

        cleanup:
        adapter.stop()
    }

    def "processEvent ignores non-message events"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def event = MAPPER.readTree('''{
            "type": "follow",
            "source": {"type": "user", "userId": "U1234567890"},
            "replyToken": "reply-token-123"
        }''')

        when:
        adapter.processEvent(event)

        then:
        0 * handler.onMessage(_)

        cleanup:
        adapter.stop()
    }

    def "processEvent ignores events without userId"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def event = MAPPER.readTree('''{
            "type": "message",
            "source": {"type": "room"},
            "message": {"id": "msg-001", "type": "text", "text": "hello"}
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
        def restrictedConfig = new LineConfig("test-token", "test-secret", true, Set.of("U0000000001"))
        def restrictedAdapter = new LineAdapter(restrictedConfig, mockHttpClient)
        def handler = Mock(ChannelMessageHandler)
        restrictedAdapter.start(handler)

        def event = MAPPER.readTree('''{
            "type": "message",
            "source": {"type": "user", "userId": "U9999999999"},
            "replyToken": "reply-token",
            "message": {"id": "msg-001", "type": "text", "text": "blocked"}
        }''')

        when:
        restrictedAdapter.processEvent(event)

        then:
        0 * handler.onMessage(_)

        cleanup:
        restrictedAdapter.stop()
    }

    def "sendMessage uses push API and returns success"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "line", "line-bot", "U1234567890", "hello")

        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockHttpClient.send(_, _) >> mockResponse

        when:
        def result = adapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Success

        cleanup:
        adapter.stop()
    }

    def "sendMessage returns failure on HTTP error"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "line", "line-bot", "U1234567890", "hello")

        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 400
        mockHttpClient.send(_, _) >> mockResponse

        when:
        def result = adapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Failure
        (result as DeliveryResult.Failure).errorCode() == "line_api_error"

        cleanup:
        adapter.stop()
    }

    def "sendMessage returns failure on exception"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "line", "line-bot", "U1234567890", "hello")

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
        def cfg = new LineConfig(null, null, false, null)

        then:
        cfg.channelAccessToken() == ""
        cfg.channelSecret() == ""
        cfg.allowedSenderIds() == Set.of()
    }

    def "config isSenderAllowed with empty set allows all"() {
        expect:
        config.isSenderAllowed("U1234567890")
        config.isSenderAllowed("U0000000000")
    }

    def "config isSenderAllowed with non-empty set filters"() {
        given:
        def cfg = new LineConfig("token", "secret", true, Set.of("U0000000001"))

        expect:
        cfg.isSenderAllowed("U0000000001")
        !cfg.isSenderAllowed("U9999999999")
    }

    def "DISABLED config has expected defaults"() {
        expect:
        !LineConfig.DISABLED.enabled()
        LineConfig.DISABLED.channelAccessToken() == ""
    }

    def "verifySignature validates correct signature"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def body = '{"events":[]}'

        // Compute expected signature
        def mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(new javax.crypto.spec.SecretKeySpec("test-secret".getBytes("UTF-8"), "HmacSHA256"))
        def hash = mac.doFinal(body.getBytes("UTF-8"))
        def validSignature = Base64.getEncoder().encodeToString(hash)

        expect:
        adapter.verifySignature(body, validSignature)
        !adapter.verifySignature(body, "invalid-signature")

        cleanup:
        adapter.stop()
    }
}
