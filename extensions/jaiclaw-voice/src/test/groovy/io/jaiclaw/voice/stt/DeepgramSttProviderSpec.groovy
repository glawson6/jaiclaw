package io.jaiclaw.voice.stt

import io.jaiclaw.core.model.TranscriptionResult
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpResponse

class DeepgramSttProviderSpec extends Specification {

    def "providerId returns deepgram"() {
        given:
        DeepgramSttProvider provider = new DeepgramSttProvider("key", null)

        expect:
        provider.providerId() == "deepgram"
    }

    def "transcribe returns result on successful response"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", "nova-2", mockHttpClient)

        String responseJson = '''
        {
          "results": {
            "channels": [{
              "alternatives": [{
                "transcript": "Hello world",
                "confidence": 0.98
              }],
              "detected_language": "en"
            }]
          }
        }'''

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> responseJson
        mockHttpClient.send(_, _) >> mockResponse

        when:
        TranscriptionResult result = provider.transcribe([1, 2, 3] as byte[], "audio/mpeg")

        then:
        result.text() == "Hello world"
        result.confidence() == 0.98
        result.language() == "en"
    }

    def "transcribe throws on HTTP error"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", "nova-2", mockHttpClient)

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 401
        mockResponse.body() >> '{"error":"unauthorized"}'
        mockHttpClient.send(_, _) >> mockResponse

        when:
        provider.transcribe([1, 2, 3] as byte[], "audio/mpeg")

        then:
        RuntimeException ex = thrown()
        ex.message.contains("401")
    }

    def "transcribe throws on connection error"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", "nova-2", mockHttpClient)
        mockHttpClient.send(_, _) >> { throw new IOException("connection refused") }

        when:
        provider.transcribe([1, 2, 3] as byte[], "audio/mpeg")

        then:
        RuntimeException ex = thrown()
        ex.message.contains("Deepgram STT transcription failed")
    }

    def "transcribe sends correct query parameters"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", "nova-2", mockHttpClient)

        String responseJson = '{"results":{"channels":[{"alternatives":[{"transcript":"test","confidence":0.9}],"detected_language":"en"}]}}'

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> responseJson

        URI capturedUri = null
        mockHttpClient.send(_, _) >> { args ->
            capturedUri = args[0].uri()
            mockResponse
        }

        when:
        provider.transcribe([1, 2, 3] as byte[], "audio/ogg")

        then:
        capturedUri.toString().contains("model=nova-2")
        capturedUri.toString().contains("detect_language=true")
        capturedUri.toString().contains("punctuate=true")
    }

    def "transcribe handles empty channels array"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", "nova-2", mockHttpClient)

        String responseJson = '{"results":{"channels":[]}}'

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> responseJson
        mockHttpClient.send(_, _) >> mockResponse

        when:
        TranscriptionResult result = provider.transcribe([1, 2, 3] as byte[], "audio/mpeg")

        then:
        result.text() == ""
        result.confidence() == 0.0
    }

    def "transcribe handles empty alternatives array"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", "nova-2", mockHttpClient)

        String responseJson = '{"results":{"channels":[{"alternatives":[],"detected_language":"fr"}]}}'

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> responseJson
        mockHttpClient.send(_, _) >> mockResponse

        when:
        TranscriptionResult result = provider.transcribe([1, 2, 3] as byte[], "audio/mpeg")

        then:
        result.text() == ""
        result.confidence() == 0.0
    }

    def "transcribe uses default mime type when null"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", "nova-2", mockHttpClient)

        String responseJson = '{"results":{"channels":[{"alternatives":[{"transcript":"ok","confidence":0.5}],"detected_language":"en"}]}}'

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> responseJson

        String capturedContentType = null
        mockHttpClient.send(_, _) >> { args ->
            capturedContentType = args[0].headers().firstValue("Content-Type").orElse(null)
            mockResponse
        }

        when:
        provider.transcribe([1, 2, 3] as byte[], null)

        then:
        capturedContentType == "audio/mpeg"
    }

    def "default model is nova-2 when null"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", null, mockHttpClient)

        String responseJson = '{"results":{"channels":[{"alternatives":[{"transcript":"test","confidence":0.9}],"detected_language":"en"}]}}'

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> responseJson

        URI capturedUri = null
        mockHttpClient.send(_, _) >> { args ->
            capturedUri = args[0].uri()
            mockResponse
        }

        when:
        provider.transcribe([1] as byte[], "audio/mpeg")

        then:
        capturedUri.toString().contains("model=nova-2")
    }

    def "transcribe detects non-English language"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        DeepgramSttProvider provider = new DeepgramSttProvider("test-key", "nova-2", mockHttpClient)

        String responseJson = '{"results":{"channels":[{"alternatives":[{"transcript":"Bonjour le monde","confidence":0.92}],"detected_language":"fr"}]}}'

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> responseJson
        mockHttpClient.send(_, _) >> mockResponse

        when:
        TranscriptionResult result = provider.transcribe([1, 2, 3] as byte[], "audio/mpeg")

        then:
        result.text() == "Bonjour le monde"
        result.language() == "fr"
        result.confidence() == 0.92
    }
}
