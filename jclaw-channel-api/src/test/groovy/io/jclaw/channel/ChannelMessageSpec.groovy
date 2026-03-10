package io.jclaw.channel

import spock.lang.Specification

class ChannelMessageSpec extends Specification {

    def "inbound factory creates INBOUND message with defaults"() {
        when:
        def msg = ChannelMessage.inbound("id1", "telegram", "bot123", "user456", "hello", Map.of())

        then:
        msg.id() == "id1"
        msg.channelId() == "telegram"
        msg.accountId() == "bot123"
        msg.peerId() == "user456"
        msg.content() == "hello"
        msg.direction() == ChannelMessage.Direction.INBOUND
        msg.attachments().isEmpty()
        msg.platformData().isEmpty()
        msg.timestamp() != null
    }

    def "outbound factory creates OUTBOUND message"() {
        when:
        def msg = ChannelMessage.outbound("id2", "slack", "workspace1", "C04ABC", "response text")

        then:
        msg.direction() == ChannelMessage.Direction.OUTBOUND
        msg.content() == "response text"
        msg.channelId() == "slack"
    }

    def "sessionKey computes correct composite key"() {
        given:
        def msg = ChannelMessage.inbound("id1", "telegram", "bot123", "user456", "hi", Map.of())

        expect:
        msg.sessionKey("default") == "default:telegram:bot123:user456"
    }

    def "null attachments and platformData default to empty"() {
        when:
        def msg = new ChannelMessage("id", "ch", "acct", "peer", "text",
                java.time.Instant.now(), ChannelMessage.Direction.INBOUND, null, null)

        then:
        msg.attachments().isEmpty()
        msg.platformData().isEmpty()
    }

    def "platformData is preserved from inbound factory"() {
        given:
        def data = Map.of("chat_id", 12345L, "message_id", 678)

        when:
        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user", "hi", data)

        then:
        msg.platformData() == data
    }
}
