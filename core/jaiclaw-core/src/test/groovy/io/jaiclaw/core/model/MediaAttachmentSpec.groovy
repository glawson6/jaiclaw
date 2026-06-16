package io.jaiclaw.core.model

import spock.lang.Specification

class MediaAttachmentSpec extends Specification {

    def "constructs a valid image attachment"() {
        when:
        def m = new MediaAttachment("image/jpeg", new byte[]{ 1, 2, 3 }, "photo.jpg")

        then:
        m.mimeType() == "image/jpeg"
        m.bytes().length == 3
        m.filename() == "photo.jpg"
        m.isImage()
        !m.isPdf()
    }

    def "constructs a valid PDF attachment"() {
        when:
        def m = new MediaAttachment("application/pdf", new byte[]{ 1 }, "doc.pdf")

        then:
        m.isPdf()
        !m.isImage()
    }

    def "isPdf is case-insensitive on the MIME type"() {
        expect:
        new MediaAttachment("APPLICATION/PDF", new byte[]{ 1 }, "x").isPdf()
        new MediaAttachment("Application/Pdf", new byte[]{ 1 }, "x").isPdf()
    }

    def "isImage matches every image/* MIME"() {
        expect:
        new MediaAttachment("image/png", new byte[]{ 1 }, "x").isImage()
        new MediaAttachment("image/webp", new byte[]{ 1 }, "x").isImage()
        new MediaAttachment("image/svg+xml", new byte[]{ 1 }, "x").isImage()
    }

    def "null filename defaults to empty string"() {
        when:
        def m = new MediaAttachment("image/jpeg", new byte[]{ 1 }, null)

        then:
        m.filename() == ""
    }

    def "rejects null mime type"() {
        when:
        new MediaAttachment(null, new byte[]{ 1 }, "x")

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("mimeType")
    }

    def "rejects blank mime type"() {
        when:
        new MediaAttachment("   ", new byte[]{ 1 }, "x")

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("mimeType")
    }

    def "rejects null bytes"() {
        when:
        new MediaAttachment("image/jpeg", null, "x")

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("bytes")
    }

    def "rejects empty byte array"() {
        when:
        new MediaAttachment("image/jpeg", new byte[0], "x")

        then:
        IllegalArgumentException ex = thrown()
        ex.message.contains("bytes")
    }
}
