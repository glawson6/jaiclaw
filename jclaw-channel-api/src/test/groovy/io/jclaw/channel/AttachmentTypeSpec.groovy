package io.jclaw.channel

import spock.lang.Specification
import spock.lang.Unroll

class AttachmentTypeSpec extends Specification {

    @Unroll
    def "fromMimeType('#mimeType') returns #expected"() {
        expect:
        AttachmentType.fromMimeType(mimeType) == expected

        where:
        mimeType                | expected
        "application/pdf"       | AttachmentType.PDF
        "image/jpeg"            | AttachmentType.IMAGE
        "image/png"             | AttachmentType.IMAGE
        "video/mp4"             | AttachmentType.VIDEO
        "video/quicktime"       | AttachmentType.VIDEO
        "audio/mpeg"            | AttachmentType.AUDIO
        "audio/wav"             | AttachmentType.AUDIO
        "application/msword"    | AttachmentType.DOCUMENT
        "text/plain"            | AttachmentType.DOCUMENT
        null                    | AttachmentType.DOCUMENT
        ""                      | AttachmentType.DOCUMENT
        "   "                   | AttachmentType.DOCUMENT
    }

    def "fromMimeType is case-insensitive"() {
        expect:
        AttachmentType.fromMimeType("Application/PDF") == AttachmentType.PDF
        AttachmentType.fromMimeType("IMAGE/JPEG") == AttachmentType.IMAGE
    }
}
