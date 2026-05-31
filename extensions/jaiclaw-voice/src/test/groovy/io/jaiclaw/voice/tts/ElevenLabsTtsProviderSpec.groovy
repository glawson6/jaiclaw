package io.jaiclaw.voice.tts

import io.jaiclaw.core.model.AudioResult
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpResponse

class ElevenLabsTtsProviderSpec extends Specification {

    def "providerId returns elevenlabs"() {
        given:
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("key", null, null)

        expect:
        provider.providerId() == "elevenlabs"
    }

    def "synthesize returns audio on successful response"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("test-key", "voice-id", "eleven_monolingual_v1", mockHttpClient)
        byte[] audioBytes = [1, 2, 3, 4, 5] as byte[]

        HttpResponse<byte[]> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> audioBytes
        mockHttpClient.send(_, _) >> mockResponse

        when:
        AudioResult result = provider.synthesize("Hello world", "voice-id", Map.of())

        then:
        result.audioData() == audioBytes
        result.mimeType() == "audio/mpeg"
    }

    def "synthesize throws on HTTP error"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("test-key", "voice-id", "eleven_monolingual_v1", mockHttpClient)

        HttpResponse<byte[]> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 401
        mockResponse.body() >> "Unauthorized".getBytes()
        mockHttpClient.send(_, _) >> mockResponse

        when:
        provider.synthesize("Hello", "voice-id", Map.of())

        then:
        RuntimeException ex = thrown()
        ex.message.contains("401")
    }

    def "synthesize throws on connection error"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("test-key", "voice-id", null, mockHttpClient)
        mockHttpClient.send(_, _) >> { throw new IOException("connection refused") }

        when:
        provider.synthesize("Hello", "voice-id", Map.of())

        then:
        RuntimeException ex = thrown()
        ex.message.contains("ElevenLabs TTS synthesis failed")
    }

    def "synthesize sends correct request with voice_id option override"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("test-key", "default-voice", "eleven_monolingual_v1", mockHttpClient)
        byte[] audioBytes = [1] as byte[]

        HttpResponse<byte[]> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> audioBytes

        URI capturedUri = null
        mockHttpClient.send(_, _) >> { args ->
            capturedUri = args[0].uri()
            mockResponse
        }

        when:
        provider.synthesize("Hello", null, Map.of("voice_id", "custom-voice-123"))

        then:
        capturedUri.toString().contains("custom-voice-123")
    }

    def "synthesize uses default voice ID when voice name is not an ID"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("test-key", "myDefaultVoiceId12345", null, mockHttpClient)
        byte[] audioBytes = [1] as byte[]

        HttpResponse<byte[]> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> audioBytes

        URI capturedUri = null
        mockHttpClient.send(_, _) >> { args ->
            capturedUri = args[0].uri()
            mockResponse
        }

        when:
        provider.synthesize("Hello", "alloy", Map.of())

        then:
        capturedUri.toString().contains("myDefaultVoiceId12345")
    }

    def "synthesize uses voice directly when it looks like an ID"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("test-key", "default-id", null, mockHttpClient)
        byte[] audioBytes = [1] as byte[]

        HttpResponse<byte[]> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> audioBytes

        URI capturedUri = null
        mockHttpClient.send(_, _) >> { args ->
            capturedUri = args[0].uri()
            mockResponse
        }

        when:
        provider.synthesize("Hello", "21m00Tcm4TlvDq8ikWAM", Map.of())

        then:
        capturedUri.toString().contains("21m00Tcm4TlvDq8ikWAM")
    }

    def "default voice ID and model are used when null"() {
        given:
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("key", null, null)

        expect:
        provider.providerId() == "elevenlabs"
        // The defaults are internal but we verify the provider constructs without error
    }

    def "synthesize with default voice parameter calls correctly"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider("test-key", "voice-id", "eleven_monolingual_v1", mockHttpClient)
        byte[] audioBytes = [1, 2, 3] as byte[]

        HttpResponse<byte[]> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> audioBytes
        mockHttpClient.send(_, _) >> mockResponse

        when:
        AudioResult result = provider.synthesize("Hello", "voice-id")

        then:
        result.audioData() == audioBytes
    }
}
