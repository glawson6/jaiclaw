package io.jclaw.channel.sms

import io.jclaw.channel.ChannelMessageHandler
import spock.lang.Specification

class SmsAdapterSpec extends Specification {

    def "channelId is sms"() {
        given:
        def adapter = new SmsAdapter(SmsConfig.DISABLED)

        expect:
        adapter.channelId() == "sms"
        adapter.displayName() == "SMS"
    }

    def "config defaults handle nulls"() {
        when:
        def cfg = new SmsConfig(null, null, null, null, false)

        then:
        cfg.accountSid() == ""
        cfg.authToken() == ""
        cfg.fromNumber() == ""
        cfg.webhookPath() == "/webhooks/sms"
        !cfg.enabled()
    }

    def "config preserves custom values"() {
        when:
        def cfg = new SmsConfig("AC123", "token", "+15551234567", "/hooks/sms", true)

        then:
        cfg.accountSid() == "AC123"
        cfg.authToken() == "token"
        cfg.fromNumber() == "+15551234567"
        cfg.webhookPath() == "/hooks/sms"
        cfg.enabled()
    }

    def "start and stop manage running state"() {
        given:
        def adapter = new SmsAdapter(SmsConfig.DISABLED)
        def handler = Mock(ChannelMessageHandler)

        expect:
        !adapter.isRunning()

        when:
        adapter.start(handler)

        then:
        adapter.isRunning()

        when:
        adapter.stop()

        then:
        !adapter.isRunning()
    }

    def "DISABLED config is not enabled"() {
        expect:
        !SmsConfig.DISABLED.enabled()
    }

    def "processWebhook dispatches inbound message"() {
        given:
        def adapter = new SmsAdapter(new SmsConfig("AC123", "token", "+15551234567", "/webhooks/sms", true))
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        when:
        adapter.processWebhook([
                From      : "+15559876543",
                Body      : "Hello from SMS",
                MessageSid: "SM123",
                NumMedia  : "0"
        ])

        then:
        1 * handler.onMessage({ msg ->
            msg.channelId() == "sms" &&
            msg.peerId() == "+15559876543" &&
            msg.content() == "Hello from SMS"
        })
    }

    def "processWebhook handles MMS attachments"() {
        given:
        def adapter = new SmsAdapter(new SmsConfig("AC123", "token", "+15551234567", "/webhooks/sms", true))
        def handler = Mock(ChannelMessageHandler)
        adapter.start(handler)

        when:
        adapter.processWebhook([
                From             : "+15559876543",
                Body             : "See this",
                MessageSid       : "SM456",
                NumMedia         : "1",
                MediaUrl0        : "https://api.twilio.com/media/123",
                MediaContentType0: "image/jpeg"
        ])

        then:
        1 * handler.onMessage({ msg ->
            msg.attachments().size() == 1 &&
            msg.attachments()[0].mimeType() == "image/jpeg"
        })
    }
}
