package io.jaiclaw.channel.signal

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.channel.DeliveryResult
import io.jaiclaw.channel.process.CliProcessBridge
import spock.lang.Specification

class SignalAdapterSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    SignalHttpClient mockHttpClient = Mock(SignalHttpClient)

    // HTTP_CLIENT mode config
    SignalConfig httpConfig = new SignalConfig(
            SignalMode.HTTP_CLIENT, "+14155551234", true,
            "http://localhost:8080", 2, "signal-cli", 7583, Set.of()
    )

    SignalAdapter httpAdapter = new SignalAdapter(httpConfig, mockHttpClient)

    def "channelId is signal"() {
        expect:
        httpAdapter.channelId() == "signal"
        httpAdapter.displayName() == "Signal"
    }

    def "start sets running state"() {
        given:
        def handler = Mock(ChannelMessageHandler)

        when:
        httpAdapter.start(handler)

        then:
        httpAdapter.isRunning()

        cleanup:
        httpAdapter.stop()
    }

    def "stop clears running state"() {
        given:
        httpAdapter.start(Mock(ChannelMessageHandler))

        when:
        httpAdapter.stop()

        then:
        !httpAdapter.isRunning()
    }

    def "HTTP_CLIENT pollMessages parses messages and dispatches to handler"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        httpAdapter.start(handler)

        def responseJson = MAPPER.readTree('''[
            {
                "envelope": {
                    "source": "+14155559999",
                    "sourceNumber": "+14155559999",
                    "dataMessage": {
                        "timestamp": 1700000000000,
                        "message": "hello from signal"
                    }
                }
            }
        ]''')
        mockHttpClient.getJson(_) >> responseJson

        when:
        httpAdapter.pollMessages()

        then:
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.channelId() == "signal" &&
            msg.peerId() == "+14155559999" &&
            msg.content() == "hello from signal" &&
            msg.direction() == ChannelMessage.Direction.INBOUND
        })

        cleanup:
        httpAdapter.stop()
    }

    def "HTTP_CLIENT pollMessages ignores empty message array"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        httpAdapter.start(handler)

        def emptyResponse = MAPPER.readTree('[]')
        mockHttpClient.getJson(_) >> emptyResponse

        when:
        httpAdapter.pollMessages()

        then:
        0 * handler.onMessage(_)

        cleanup:
        httpAdapter.stop()
    }

    def "HTTP_CLIENT pollMessages ignores messages without dataMessage"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        httpAdapter.start(handler)

        def responseJson = MAPPER.readTree('''[
            {
                "envelope": {
                    "source": "+14155559999",
                    "typingMessage": {}
                }
            }
        ]''')
        mockHttpClient.getJson(_) >> responseJson

        when:
        httpAdapter.pollMessages()

        then:
        0 * handler.onMessage(_)

        cleanup:
        httpAdapter.stop()
    }

    def "HTTP_CLIENT sendMessage posts to /v2/send"() {
        given:
        httpAdapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "signal", "+14155551234", "+14155559999", "hi there")

        def sendResponse = MAPPER.readTree('{"timestamp": "12345"}')
        mockHttpClient.postJson(_, _) >> sendResponse

        when:
        def result = httpAdapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Success
        (result as DeliveryResult.Success).platformMessageId() == "12345"

        cleanup:
        httpAdapter.stop()
    }

    def "HTTP_CLIENT sendMessage returns failure on error"() {
        given:
        httpAdapter.start(Mock(ChannelMessageHandler))
        def outbound = ChannelMessage.outbound("msg1", "signal", "+14155551234", "+14155559999", "hi there")

        mockHttpClient.postJson(_, _) >> { throw new RuntimeException("connection refused") }

        when:
        def result = httpAdapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Failure
        (result as DeliveryResult.Failure).message() == "connection refused"

        cleanup:
        httpAdapter.stop()
    }

    def "allowed-sender filter drops unauthorized messages"() {
        given:
        def restrictedConfig = new SignalConfig(
                SignalMode.HTTP_CLIENT, "+14155551234", true,
                "http://localhost:8080", 2, "signal-cli", 7583,
                Set.of("+14155550001")
        )
        def adapter = new SignalAdapter(restrictedConfig, mockHttpClient)
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def responseJson = MAPPER.readTree('''[
            {
                "envelope": {
                    "source": "+14155559999",
                    "dataMessage": {
                        "timestamp": 1700000000000,
                        "message": "blocked user"
                    }
                }
            }
        ]''')
        mockHttpClient.getJson(_) >> responseJson

        when:
        adapter.pollMessages()

        then:
        0 * handler.onMessage(_)

        cleanup:
        adapter.stop()
    }

    def "allowed-sender filter accepts authorized messages"() {
        given:
        def restrictedConfig = new SignalConfig(
                SignalMode.HTTP_CLIENT, "+14155551234", true,
                "http://localhost:8080", 2, "signal-cli", 7583,
                Set.of("+14155559999")
        )
        def adapter = new SignalAdapter(restrictedConfig, mockHttpClient)
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        def responseJson = MAPPER.readTree('''[
            {
                "envelope": {
                    "source": "+14155559999",
                    "dataMessage": {
                        "timestamp": 1700000000000,
                        "message": "allowed user"
                    }
                }
            }
        ]''')
        mockHttpClient.getJson(_) >> responseJson

        when:
        adapter.pollMessages()

        then:
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.content() == "allowed user"
        })

        cleanup:
        adapter.stop()
    }

    def "processEnvelope handles sourceNumber fallback"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        httpAdapter.start(handler)

        def envelope = MAPPER.readTree('''{
            "sourceNumber": "+14155559999",
            "dataMessage": {
                "timestamp": 1700000000000,
                "message": "via sourceNumber"
            }
        }''')

        when:
        httpAdapter.processEnvelope(envelope)

        then:
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.peerId() == "+14155559999" &&
            msg.content() == "via sourceNumber"
        })

        cleanup:
        httpAdapter.stop()
    }

    def "processEnvelope ignores empty text messages"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        httpAdapter.start(handler)

        def envelope = MAPPER.readTree('''{
            "source": "+14155559999",
            "dataMessage": {
                "timestamp": 1700000000000,
                "message": ""
            }
        }''')

        when:
        httpAdapter.processEnvelope(envelope)

        then:
        0 * handler.onMessage(_)

        cleanup:
        httpAdapter.stop()
    }

    def "processEnvelope ignores missing sender"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        httpAdapter.start(handler)

        def envelope = MAPPER.readTree('''{
            "dataMessage": {
                "timestamp": 1700000000000,
                "message": "no sender"
            }
        }''')

        when:
        httpAdapter.processEnvelope(envelope)

        then:
        0 * handler.onMessage(_)

        cleanup:
        httpAdapter.stop()
    }

    def "EMBEDDED mode delegates send to CliProcessBridge"() {
        given:
        def embeddedConfig = new SignalConfig(
                SignalMode.EMBEDDED, "+14155551234", true,
                "http://localhost:8080", 2, "signal-cli", 7583, Set.of()
        )
        def mockBridge = Mock(CliProcessBridge)
        def adapter = new SignalAdapter(embeddedConfig, mockHttpClient, mockBridge)
        adapter.start(Mock(ChannelMessageHandler))

        def outbound = ChannelMessage.outbound("msg1", "signal", "+14155551234", "+14155559999", "hello embedded")
        def resultNode = MAPPER.readTree('{"timestamp": "67890"}')
        mockBridge.sendRequest("send", _) >> resultNode

        when:
        def result = adapter.sendMessage(outbound)

        then:
        result instanceof DeliveryResult.Success
        (result as DeliveryResult.Success).platformMessageId() == "67890"

        cleanup:
        adapter.stop()
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new SignalConfig(null, null, false, null, 0, null, 0, null)

        then:
        cfg.mode() == SignalMode.HTTP_CLIENT
        cfg.phoneNumber() == ""
        cfg.apiUrl() == "http://localhost:8080"
        cfg.pollIntervalSeconds() == 2
        cfg.cliCommand() == "signal-cli"
        cfg.tcpPort() == 7583
        cfg.allowedSenderIds() == Set.of()
    }

    def "config isSenderAllowed with empty set allows all"() {
        given:
        def cfg = new SignalConfig(
                SignalMode.HTTP_CLIENT, "+14155551234", true,
                "http://localhost:8080", 2, "signal-cli", 7583, Set.of()
        )

        expect:
        cfg.isSenderAllowed("+14155559999")
        cfg.isSenderAllowed("+10000000000")
    }

    def "config isSenderAllowed with non-empty set filters"() {
        given:
        def cfg = new SignalConfig(
                SignalMode.HTTP_CLIENT, "+14155551234", true,
                "http://localhost:8080", 2, "signal-cli", 7583,
                Set.of("+14155550001")
        )

        expect:
        cfg.isSenderAllowed("+14155550001")
        !cfg.isSenderAllowed("+14155559999")
    }

    def "DISABLED config has expected defaults"() {
        expect:
        !SignalConfig.DISABLED.enabled()
        SignalConfig.DISABLED.mode() == SignalMode.HTTP_CLIENT
    }
}
