package io.jclaw.channel.discord

import io.jclaw.channel.ChannelMessageHandler
import io.jclaw.gateway.WebhookDispatcher
import spock.lang.Specification

class DiscordAdapterSpec extends Specification {

    WebhookDispatcher webhookDispatcher = new WebhookDispatcher()

    // Webhook mode config (useGateway = false)
    DiscordConfig webhookConfig = new DiscordConfig("test-bot-token", "app-id-123", true)
    DiscordAdapter webhookAdapter = new DiscordAdapter(webhookConfig, webhookDispatcher)

    def "channelId is discord"() {
        expect:
        webhookAdapter.channelId() == "discord"
        webhookAdapter.displayName() == "Discord"
    }

    def "webhook mode registers webhook and sets running"() {
        given:
        def handler = Mock(ChannelMessageHandler)

        when:
        webhookAdapter.start(handler)

        then:
        webhookAdapter.isRunning()
        webhookDispatcher.registeredChannels().contains("discord")
    }

    def "stop clears running state"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))

        when:
        webhookAdapter.stop()

        then:
        !webhookAdapter.isRunning()
    }

    def "webhook handles PING verification"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))
        def ping = '{"type": 1}'

        when:
        def response = webhookDispatcher.dispatch("discord", ping, Map.of())

        then:
        response.statusCode.value() == 200
        response.body == '{"type": 1}'
    }

    def "webhook handles interaction with content"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)
        def interaction = '''
        {
            "type": 2,
            "id": "interaction-123",
            "channel_id": "ch-456",
            "guild_id": "guild-789",
            "member": {
                "user": {"id": "user-012"}
            },
            "data": {
                "content": "hello discord"
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("discord", interaction, Map.of())

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage({ msg ->
            msg.channelId() == "discord" &&
            msg.peerId() == "ch-456" &&
            msg.content() == "hello discord" &&
            msg.accountId() == "guild-789"
        })
    }

    def "webhook handles malformed JSON gracefully"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))

        when:
        def response = webhookDispatcher.dispatch("discord", "not json", Map.of())

        then:
        response.statusCode.value() == 200
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new DiscordConfig(null, null, false)

        then:
        cfg.botToken() == ""
        cfg.applicationId() == ""
        !cfg.useGateway()
    }

    def "3-arg constructor defaults to webhook mode"() {
        when:
        def cfg = new DiscordConfig("token", "app-id", true)

        then:
        !cfg.useGateway()
    }

    def "4-arg constructor with useGateway true"() {
        when:
        def cfg = new DiscordConfig("token", "app-id", true, true)

        then:
        cfg.useGateway()
    }
}
