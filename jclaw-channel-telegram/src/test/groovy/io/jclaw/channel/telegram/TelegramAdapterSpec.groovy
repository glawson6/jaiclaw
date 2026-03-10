package io.jclaw.channel.telegram

import io.jclaw.channel.ChannelMessage
import io.jclaw.channel.ChannelMessageHandler
import io.jclaw.channel.DeliveryResult
import io.jclaw.gateway.WebhookDispatcher
import spock.lang.Specification

class TelegramAdapterSpec extends Specification {

    WebhookDispatcher webhookDispatcher = new WebhookDispatcher()

    // Webhook mode config (has webhookUrl set)
    TelegramConfig webhookConfig = new TelegramConfig("test-bot-token", "https://example.com/webhook/telegram", true)
    TelegramAdapter webhookAdapter = new TelegramAdapter(webhookConfig, webhookDispatcher)

    def "channelId is telegram"() {
        expect:
        webhookAdapter.channelId() == "telegram"
        webhookAdapter.displayName() == "Telegram"
    }

    def "webhook mode registers webhook handler and sets running"() {
        given:
        def handler = Mock(ChannelMessageHandler)

        when:
        webhookAdapter.start(handler)

        then:
        webhookAdapter.isRunning()
        webhookDispatcher.registeredChannels().contains("telegram")
    }

    def "stop clears running state"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))

        when:
        webhookAdapter.stop()

        then:
        !webhookAdapter.isRunning()
    }

    def "webhook parses text message and dispatches to handler"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)
        def updateJson = '''
        {
            "update_id": 123456,
            "message": {
                "message_id": 789,
                "from": {"id": 111, "first_name": "Test"},
                "chat": {"id": 222, "type": "private"},
                "text": "hello bot"
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("telegram", updateJson, Map.of())

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.channelId() == "telegram" &&
            msg.peerId() == "222" &&
            msg.content() == "hello bot" &&
            msg.direction() == ChannelMessage.Direction.INBOUND
        })
    }

    def "webhook ignores non-text updates"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)
        def stickerUpdate = '''
        {
            "update_id": 123456,
            "message": {
                "message_id": 789,
                "from": {"id": 111},
                "chat": {"id": 222},
                "sticker": {"file_id": "sticker123"}
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("telegram", stickerUpdate, Map.of())

        then:
        response.statusCode.value() == 200
        0 * handler.onMessage(_)
    }

    def "webhook handles malformed JSON gracefully"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))

        when:
        def response = webhookDispatcher.dispatch("telegram", "not json", Map.of())

        then:
        response.statusCode.value() == 200 // Always return 200 to Telegram
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new TelegramConfig(null, null, false)

        then:
        cfg.botToken() == ""
        cfg.webhookUrl() == ""
        !cfg.enabled()
        cfg.pollingTimeoutSeconds() == 30
    }

    def "config usePolling returns true when webhookUrl is blank"() {
        expect:
        new TelegramConfig("token", "", true).usePolling()
        new TelegramConfig("token", null, true).usePolling()
        !new TelegramConfig("token", "https://example.com/webhook", true).usePolling()
    }

    def "config custom polling timeout"() {
        when:
        def cfg = new TelegramConfig("token", "", true, 60)

        then:
        cfg.pollingTimeoutSeconds() == 60
    }

    def "config negative polling timeout defaults to 30"() {
        when:
        def cfg = new TelegramConfig("token", "", true, -1)

        then:
        cfg.pollingTimeoutSeconds() == 30
    }
}
