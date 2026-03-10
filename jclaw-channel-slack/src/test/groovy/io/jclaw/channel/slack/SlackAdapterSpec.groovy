package io.jclaw.channel.slack

import io.jclaw.channel.ChannelMessage
import io.jclaw.channel.ChannelMessageHandler
import io.jclaw.gateway.WebhookDispatcher
import spock.lang.Specification

class SlackAdapterSpec extends Specification {

    WebhookDispatcher webhookDispatcher = new WebhookDispatcher()

    // Webhook mode config (no appToken)
    SlackConfig webhookConfig = new SlackConfig("xoxb-test-token", "test-signing-secret", true)
    SlackAdapter webhookAdapter = new SlackAdapter(webhookConfig, webhookDispatcher)

    def "channelId is slack"() {
        expect:
        webhookAdapter.channelId() == "slack"
        webhookAdapter.displayName() == "Slack"
    }

    def "webhook mode registers webhook and sets running"() {
        given:
        def handler = Mock(ChannelMessageHandler)

        when:
        webhookAdapter.start(handler)

        then:
        webhookAdapter.isRunning()
        webhookDispatcher.registeredChannels().contains("slack")
    }

    def "stop clears running state"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))

        when:
        webhookAdapter.stop()

        then:
        !webhookAdapter.isRunning()
    }

    def "webhook handles url_verification challenge"() {
        given:
        webhookAdapter.start(Mock(ChannelMessageHandler))
        def challenge = '''
        {
            "type": "url_verification",
            "challenge": "test_challenge_token",
            "token": "verification_token"
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("slack", challenge, Map.of())

        then:
        response.statusCode.value() == 200
        response.body == "test_challenge_token"
    }

    def "webhook parses message event and dispatches to handler"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)
        def eventPayload = '''
        {
            "type": "event_callback",
            "team_id": "T12345",
            "event_id": "Ev12345",
            "event": {
                "type": "message",
                "text": "hello from slack",
                "channel": "C04ABCDEF",
                "user": "U12345",
                "ts": "1234567890.123456"
            }
        }
        '''

        when:
        def response = webhookDispatcher.dispatch("slack", eventPayload, Map.of())

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage({ ChannelMessage msg ->
            msg.channelId() == "slack" &&
            msg.peerId() == "C04ABCDEF" &&
            msg.content() == "hello from slack" &&
            msg.accountId() == "T12345"
        })
    }

    def "webhook ignores bot messages"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)
        def botMessage = '''
        {
            "type": "event_callback",
            "team_id": "T12345",
            "event_id": "Ev99",
            "event": {
                "type": "message",
                "text": "bot says hi",
                "channel": "C04ABCDEF",
                "bot_id": "B12345"
            }
        }
        '''

        when:
        webhookDispatcher.dispatch("slack", botMessage, Map.of())

        then:
        0 * handler.onMessage(_)
    }

    def "webhook ignores message subtypes"() {
        given:
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)
        def subtypeMessage = '''
        {
            "type": "event_callback",
            "team_id": "T12345",
            "event_id": "Ev99",
            "event": {
                "type": "message",
                "subtype": "channel_join",
                "text": "joined",
                "channel": "C04ABCDEF"
            }
        }
        '''

        when:
        webhookDispatcher.dispatch("slack", subtypeMessage, Map.of())

        then:
        0 * handler.onMessage(_)
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new SlackConfig(null, null, false)

        then:
        cfg.botToken() == ""
        cfg.signingSecret() == ""
        cfg.appToken() == ""
    }

    def "config useSocketMode returns true when appToken is set"() {
        expect:
        new SlackConfig("token", "secret", true, "xapp-test").useSocketMode()
        !new SlackConfig("token", "secret", true, "").useSocketMode()
        !new SlackConfig("token", "secret", true).useSocketMode()
    }

    def "3-arg constructor defaults to webhook mode"() {
        when:
        def cfg = new SlackConfig("token", "secret", true)

        then:
        !cfg.useSocketMode()
        cfg.appToken() == ""
    }

    def "4-arg constructor with null appToken defaults to empty"() {
        when:
        def cfg = new SlackConfig("token", "secret", true, null)

        then:
        !cfg.useSocketMode()
        cfg.appToken() == ""
    }
}
