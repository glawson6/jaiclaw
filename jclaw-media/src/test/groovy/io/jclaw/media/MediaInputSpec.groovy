package io.jclaw.media

import spock.lang.Specification

class MediaInputSpec extends Specification {

    def "of factory creates valid input"() {
        when:
        def input = MediaInput.of([1, 2, 3] as byte[], "image/jpeg", "photo.jpg")

        then:
        input.mimeType() == "image/jpeg"
        input.filename() == "photo.jpg"
        input.sizeBytes() == 3
        input.metadata() == [:]
    }

    def "rejects null data"() {
        when:
        new MediaInput(null, "image/jpeg", "test.jpg", [:])

        then:
        thrown(IllegalArgumentException)
    }

    def "rejects blank mimeType"() {
        when:
        new MediaInput([1] as byte[], "", "test.jpg", [:])

        then:
        thrown(IllegalArgumentException)
    }

    def "defaults null filename"() {
        when:
        def input = new MediaInput([1] as byte[], "video/mp4", null, [:])

        then:
        input.filename() == "unknown"
    }

    def "isImage detects image types"() {
        expect:
        MediaInput.of([1] as byte[], "image/jpeg", "a.jpg").isImage()
        MediaInput.of([1] as byte[], "image/png", "a.png").isImage()
        !MediaInput.of([1] as byte[], "video/mp4", "a.mp4").isImage()
    }

    def "isVideo detects video types"() {
        expect:
        MediaInput.of([1] as byte[], "video/mp4", "a.mp4").isVideo()
        !MediaInput.of([1] as byte[], "image/jpeg", "a.jpg").isVideo()
    }

    def "isAudio detects audio types"() {
        expect:
        MediaInput.of([1] as byte[], "audio/mpeg", "a.mp3").isAudio()
        !MediaInput.of([1] as byte[], "video/mp4", "a.mp4").isAudio()
    }
}
