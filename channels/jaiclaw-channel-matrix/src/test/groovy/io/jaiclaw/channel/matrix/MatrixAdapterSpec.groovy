package io.jaiclaw.channel.matrix

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.channel.DeliveryResult
import io.jaiclaw.channel.chunking.PlatformLimits
import spock.lang.Specification

class MatrixAdapterSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    MatrixApiClient mockApiClient = Mock(MatrixApiClient)

    MatrixConfig config = new MatrixConfig(
            "https://matrix.example.com", "test-token", "@bot:example.com",
            true, 30000, Set.of()
    )

    MatrixAdapter adapter = new MatrixAdapter(config, mockApiClient)

    def "channelId is matrix"() {
        expect:
        adapter.channelId() == "matrix"
        adapter.displayName() == "Matrix"
    }

    def "platformLimits returns Matrix limits"() {
        expect:
        adapter.platformLimits() == PlatformLimits.MATRIX
        adapter.platformLimits().maxTextLength() == 65536
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

    def "pollSync dispatches messages to handler"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def syncResponse = MAPPER.readTree('''{
            "next_batch": "s123_456",
            "rooms": {
                "join": {
                    "!room1:example.com": {
                        "timeline": {
                            "events": [
                                {
                                    "type": "m.room.message",
                                    "sender": "@alice:example.com",
                                    "event_id": "$evt1",
                                    "content": {
                                        "msgtype": "m.text",
                                        "body": "hello from Matrix"
                                    }
                                }
                            ]
                        }
                    }
                }
            }
        }''')
        mockApiClient.sync(null, 30000) >> syncResponse

        when:
        adapter.pollSync()

        then:
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.channelId() == "matrix" &&
            msg.peerId() == "@alice:example.com" &&
            msg.accountId() == "!room1:example.com" &&
            msg.content() == "hello from Matrix" &&
            msg.direction() == ChannelMessage.Direction.INBOUND
        })
        adapter.getSinceToken() == "s123_456"

        cleanup:
        adapter.stop()
    }

    def "pollSync ignores bot's own messages"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def syncResponse = MAPPER.readTree('''{
            "next_batch": "s123_457",
            "rooms": {
                "join": {
                    "!room1:example.com": {
                        "timeline": {
                            "events": [
                                {
                                    "type": "m.room.message",
                                    "sender": "@bot:example.com",
                                    "event_id": "$evt2",
                                    "content": {
                                        "msgtype": "m.text",
                                        "body": "bot's own message"
                                    }
                                }
                            ]
                        }
                    }
                }
            }
        }''')
        mockApiClient.sync(null, 30000) >> syncResponse

        when:
        adapter.pollSync()

        then:
        0 * handler.onMessage(_)

        cleanup:
        adapter.stop()
    }

    def "pollSync filters non-allowed senders"() {
        given:
        def restrictedConfig = new MatrixConfig(
                "https://matrix.example.com", "test-token", "@bot:example.com",
                true, 30000, Set.of("@allowed:example.com")
        )
        def restrictedAdapter = new MatrixAdapter(restrictedConfig, mockApiClient)
        def handler = Mock(ChannelMessageHandler)
        restrictedAdapter.start(handler)

        def syncResponse = MAPPER.readTree('''{
            "next_batch": "s123_458",
            "rooms": {
                "join": {
                    "!room1:example.com": {
                        "timeline": {
                            "events": [
                                {
                                    "type": "m.room.message",
                                    "sender": "@blocked:example.com",
                                    "event_id": "$evt3",
                                    "content": {
                                        "msgtype": "m.text",
                                        "body": "blocked sender"
                                    }
                                }
                            ]
                        }
                    }
                }
            }
        }''')
        mockApiClient.sync(null, 30000) >> syncResponse

        when:
        restrictedAdapter.pollSync()

        then:
        0 * handler.onMessage(_)

        cleanup:
        restrictedAdapter.stop()
    }

    def "sendMessage delegates to API client"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "matrix", "!room1:example.com", "@alice:example.com", "hello")
        mockApiClient.sendMessage("!room1:example.com", "hello") >> "\$sent-event-1"

        when:
        def result = adapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Success
        (result as DeliveryResult.Success).platformMessageId() == "\$sent-event-1"

        cleanup:
        adapter.stop()
    }

    def "sendMessage returns failure when no room ID"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "matrix", "", "@alice:example.com", "hello")

        when:
        def result = adapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Failure
        (result as DeliveryResult.Failure).errorCode() == "no_room"

        cleanup:
        adapter.stop()
    }

    def "sendMessage returns failure on exception"() {
        given:
        adapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "matrix", "!room1:example.com", "@alice:example.com", "hello")
        mockApiClient.sendMessage("!room1:example.com", "hello") >> { throw new IOException("connection refused") }

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
        def cfg = new MatrixConfig(null, null, null, false, 0, null)

        then:
        cfg.homeserverUrl() == ""
        cfg.accessToken() == ""
        cfg.userId() == ""
        cfg.syncTimeoutMs() == 30000
        cfg.allowedSenderIds() == Set.of()
    }

    def "config isSenderAllowed with empty set allows all"() {
        expect:
        config.isSenderAllowed("@anyone:example.com")
    }

    def "config isSenderAllowed with non-empty set filters"() {
        given:
        def cfg = new MatrixConfig("url", "token", "@bot:ex", true, 30000, Set.of("@allowed:ex"))

        expect:
        cfg.isSenderAllowed("@allowed:ex")
        !cfg.isSenderAllowed("@blocked:ex")
    }

    def "DISABLED config has expected defaults"() {
        expect:
        !MatrixConfig.DISABLED.enabled()
        MatrixConfig.DISABLED.homeserverUrl() == ""
    }
}
