package io.jclaw.channel

import spock.lang.Specification

class AttachmentPayloadSpec extends Specification {

    def "of() factory creates payload with correct type and size"() {
        given:
        byte[] content = "PDF content".bytes

        when:
        def payload = AttachmentPayload.of("report.pdf", "application/pdf", content)

        then:
        payload.filename() == "report.pdf"
        payload.type() == AttachmentType.PDF
        payload.mimeType() == "application/pdf"
        payload.bytes() == content
        payload.sizeBytes() == content.length
    }

    def "of() factory auto-detects image type"() {
        given:
        byte[] content = [0xFF, 0xD8] as byte[]

        when:
        def payload = AttachmentPayload.of("photo.jpg", "image/jpeg", content)

        then:
        payload.type() == AttachmentType.IMAGE
    }

    def "constructor rejects null filename"() {
        when:
        new AttachmentPayload(null, AttachmentType.PDF, new byte[0], "application/pdf", 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor rejects blank filename"() {
        when:
        new AttachmentPayload("  ", AttachmentType.PDF, new byte[0], "application/pdf", 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor rejects null type"() {
        when:
        new AttachmentPayload("file.pdf", null, new byte[0], "application/pdf", 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor rejects null bytes"() {
        when:
        new AttachmentPayload("file.pdf", AttachmentType.PDF, null, "application/pdf", 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor rejects negative sizeBytes"() {
        when:
        new AttachmentPayload("file.pdf", AttachmentType.PDF, new byte[0], "application/pdf", -1)

        then:
        thrown(IllegalArgumentException)
    }
}
