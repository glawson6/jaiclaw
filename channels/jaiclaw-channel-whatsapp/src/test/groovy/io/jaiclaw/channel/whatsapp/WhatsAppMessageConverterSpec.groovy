package io.jaiclaw.channel.whatsapp

import io.jaiclaw.channel.ChannelMessage
import spock.lang.Specification

class WhatsAppMessageConverterSpec extends Specification {

    def "CHANNEL_ID is whatsapp"() {
        expect:
        WhatsAppMessageConverter.CHANNEL_ID == "whatsapp"
    }

    def "WhatsAppProperties defaults webhookPath"() {
        when:
        def props = new WhatsAppProperties("123", "token", "verify", null)

        then:
        props.webhookPath() == "/webhook/whatsapp"
    }

    def "WhatsAppProperties preserves explicit webhookPath"() {
        when:
        def props = new WhatsAppProperties("123", "token", "verify", "/custom/path")

        then:
        props.webhookPath() == "/custom/path"
    }

    def "WhatsAppProperties blank webhookPath defaults"() {
        when:
        def props = new WhatsAppProperties("123", "token", "verify", "  ")

        then:
        props.webhookPath() == "/webhook/whatsapp"
    }
}
