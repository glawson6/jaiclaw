package io.jaiclaw.channel.slack

import io.jaiclaw.channel.ChannelMessage
import io.jaiclaw.channel.ChannelMessageHandler
import io.jaiclaw.gateway.WebhookDispatcher
import spock.lang.Specification

import java.util.HexFormat

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

    // --- Signature verification tests ---

    def "webhook rejects request with invalid signature when verifySignature is enabled"() {
        given:
        def verifyConfig = new SlackConfig("xoxb-test-token", "test-signing-secret", true, "", Set.of(), true)
        def verifyAdapter = new SlackAdapter(verifyConfig, webhookDispatcher)
        def handler = Mock(ChannelMessageHandler)
        verifyAdapter.start(handler)

        def body = '{"type":"event_callback","event":{"type":"message","text":"hi","channel":"C1","user":"U1"}}'
        def headers = Map.of(
            "x-slack-signature", "v0=invalid",
            "x-slack-request-timestamp", String.valueOf(System.currentTimeMillis() / 1000 as long)
        )

        when:
        def response = webhookDispatcher.dispatch("slack", body, headers)

        then:
        response.statusCode.value() == 401
        0 * handler.onMessage(_)
    }

    def "webhook accepts request with valid signature when verifySignature is enabled"() {
        given:
        def signingSecret = "test-signing-secret"
        def verifyConfig = new SlackConfig("xoxb-test-token", signingSecret, true, "", Set.of(), true)
        def verifyAdapter = new SlackAdapter(verifyConfig, webhookDispatcher)
        def handler = Mock(ChannelMessageHandler)
        verifyAdapter.start(handler)

        def body = '{"type":"event_callback","team_id":"T1","event_id":"E1","event":{"type":"message","text":"hi","channel":"C1","user":"U1","ts":"123"}}'
        def timestamp = String.valueOf(System.currentTimeMillis() / 1000 as long)

        // Compute valid HMAC-SHA256 signature
        def baseString = "v0:${timestamp}:${body}"
        def mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(new javax.crypto.spec.SecretKeySpec(signingSecret.getBytes("UTF-8"), "HmacSHA256"))
        def hash = mac.doFinal(baseString.getBytes("UTF-8"))
        def signature = "v0=" + HexFormat.of().formatHex(hash)

        def headers = Map.of(
            "x-slack-signature", signature,
            "x-slack-request-timestamp", timestamp
        )

        when:
        def response = webhookDispatcher.dispatch("slack", body, headers)

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage(_)
    }

    def "webhook skips verification when verifySignature is disabled even with signingSecret"() {
        given:
        // Default config has verifySignature=false (3-arg constructor)
        def handler = Mock(ChannelMessageHandler)
        webhookAdapter.start(handler)

        def body = '{"type":"event_callback","team_id":"T1","event_id":"E1","event":{"type":"message","text":"hi","channel":"C1","user":"U1","ts":"123"}}'

        when:
        // No signature headers — should still pass since verifySignature is false
        def response = webhookDispatcher.dispatch("slack", body, Map.of())

        then:
        response.statusCode.value() == 200
        1 * handler.onMessage(_)
    }

    def "webhook rejects stale timestamp when verifySignature is enabled"() {
        given:
        def signingSecret = "test-signing-secret"
        def verifyConfig = new SlackConfig("xoxb-test-token", signingSecret, true, "", Set.of(), true)
        def verifyAdapter = new SlackAdapter(verifyConfig, webhookDispatcher)
        verifyAdapter.start(Mock(ChannelMessageHandler))

        def body = '{"type":"event_callback"}'
        def staleTimestamp = String.valueOf((System.currentTimeMillis() / 1000 as long) - 600)

        // Compute valid signature but with stale timestamp
        def baseString = "v0:${staleTimestamp}:${body}"
        def mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(new javax.crypto.spec.SecretKeySpec(signingSecret.getBytes("UTF-8"), "HmacSHA256"))
        def hash = mac.doFinal(baseString.getBytes("UTF-8"))
        def signature = "v0=" + HexFormat.of().formatHex(hash)

        def headers = Map.of(
            "x-slack-signature", signature,
            "x-slack-request-timestamp", staleTimestamp
        )

        when:
        def response = webhookDispatcher.dispatch("slack", body, headers)

        then:
        response.statusCode.value() == 401
    }
}
