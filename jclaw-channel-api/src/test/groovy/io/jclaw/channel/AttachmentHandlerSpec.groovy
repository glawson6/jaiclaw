package io.jclaw.channel

import spock.lang.Specification

class AttachmentHandlerSpec extends Specification {

    def "AttachmentHandler can be implemented and invoked"() {
        given:
        def pdfBytes = "PDF content".bytes
        def handler = new AttachmentHandler() {
            @Override
            boolean supports(AttachmentType type) {
                return type == AttachmentType.PDF
            }

            @Override
            List<AttachmentPayload> extract(ChannelMessage message) {
                if (message.hasAttachments()) {
                    return message.attachments().collect { att ->
                        AttachmentPayload.of(att.name(), att.mimeType(), att.data())
                    }
                }
                return []
            }
        }

        and:
        def attachment = new ChannelMessage.Attachment("report.pdf", "application/pdf", null, pdfBytes)
        def msg = ChannelMessage.inbound("id1", "telegram", "bot", "user",
                "upload this", List.of(attachment), Map.of())

        expect:
        handler.supports(AttachmentType.PDF)
        !handler.supports(AttachmentType.VIDEO)

        when:
        def payloads = handler.extract(msg)

        then:
        payloads.size() == 1
        payloads[0].filename() == "report.pdf"
        payloads[0].type() == AttachmentType.PDF
        payloads[0].bytes() == pdfBytes
    }

    def "hasAttachments returns true when message has attachments"() {
        given:
        def attachment = new ChannelMessage.Attachment("file.pdf", "application/pdf", null, new byte[0])
        def msg = ChannelMessage.inbound("id1", "ch", "acct", "peer",
                "text", List.of(attachment), Map.of())

        expect:
        msg.hasAttachments()
    }

    def "hasAttachments returns false for plain text messages"() {
        given:
        def msg = ChannelMessage.inbound("id1", "ch", "acct", "peer", "text", Map.of())

        expect:
        !msg.hasAttachments()
    }
}
