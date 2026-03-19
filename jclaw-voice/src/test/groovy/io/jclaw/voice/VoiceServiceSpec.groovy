package io.jclaw.voice

import io.jclaw.core.model.AudioResult
import io.jclaw.core.model.TranscriptionResult
import io.jclaw.voice.tts.TtsProvider
import io.jclaw.voice.stt.SttProvider
import spock.lang.Specification

class VoiceServiceSpec extends Specification {

    def "synthesize returns result from first successful provider"() {
        given:
        def provider = Stub(TtsProvider) {
            synthesize(_, _, _) >> new AudioResult([1, 2, 3] as byte[], "audio/mpeg", 1000)
        }
        def service = new VoiceService([provider], [], "alloy")

        when:
        def result = service.synthesize("Hello")

        then:
        result.isPresent()
        result.get().mimeType() == "audio/mpeg"
    }

    def "synthesize falls back to next provider on failure"() {
        given:
        def failing = Stub(TtsProvider) {
            synthesize(_, _, _) >> { throw new RuntimeException("fail") }
            providerId() >> "failing"
        }
        def working = Stub(TtsProvider) {
            synthesize(_, _, _) >> new AudioResult([1] as byte[], "audio/mpeg", 500)
            providerId() >> "working"
        }
        def service = new VoiceService([failing, working], [], "alloy")

        when:
        def result = service.synthesize("Hello")

        then:
        result.isPresent()
    }

    def "synthesize returns empty when all providers fail"() {
        given:
        def failing = Stub(TtsProvider) {
            synthesize(_, _, _) >> { throw new RuntimeException("fail") }
            providerId() >> "failing"
        }
        def service = new VoiceService([failing], [], "alloy")

        when:
        def result = service.synthesize("Hello")

        then:
        result.isEmpty()
    }

    def "transcribe returns result from provider"() {
        given:
        def provider = Stub(SttProvider) {
            transcribe(_, _) >> new TranscriptionResult("Hello world", "en", 0.95)
        }
        def service = new VoiceService([], [provider], "alloy")

        when:
        def result = service.transcribe([1, 2, 3] as byte[], "audio/ogg")

        then:
        result.isPresent()
        result.get().text() == "Hello world"
    }
}
