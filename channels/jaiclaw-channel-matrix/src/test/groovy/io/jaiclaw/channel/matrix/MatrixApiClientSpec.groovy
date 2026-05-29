package io.jaiclaw.channel.matrix

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpResponse

class MatrixApiClientSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    HttpClient mockHttpClient = Mock(HttpClient)

    MatrixApiClient client = new MatrixApiClient(
            "https://matrix.example.com", "test-token", mockHttpClient
    )

    def "sync sends GET with correct parameters"() {
        given:
        def syncJson = MAPPER.readTree('{"next_batch": "s1_2", "rooms": {}}')
        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> syncJson.toString()

        when:
        def result = client.sync("s0_1", 30000)

        then:
        1 * mockHttpClient.send({ java.net.http.HttpRequest req ->
            req.uri().toString().contains("/_matrix/client/v3/sync") &&
            req.uri().toString().contains("timeout=30000") &&
            req.uri().toString().contains("since=s0_1") &&
            req.method() == "GET"
        }, _) >> mockResponse

        result.path("next_batch").asText() == "s1_2"
    }

    def "sync without since token omits since parameter"() {
        given:
        def syncJson = MAPPER.readTree('{"next_batch": "s0_1", "rooms": {}}')
        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> syncJson.toString()

        when:
        def result = client.sync(null, 30000)

        then:
        1 * mockHttpClient.send({ java.net.http.HttpRequest req ->
            req.uri().toString().contains("/_matrix/client/v3/sync") &&
            !req.uri().toString().contains("since=")
        }, _) >> mockResponse

        result.path("next_batch").asText() == "s0_1"
    }

    def "sync throws on non-2xx response"() {
        given:
        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 401
        mockHttpClient.send(_, _) >> mockResponse

        when:
        client.sync(null, 30000)

        then:
        thrown(IOException)
    }

    def "sendMessage sends PUT with correct path and body"() {
        given:
        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> '{"event_id": "$evt123"}'

        when:
        def eventId = client.sendMessage("!room1:example.com", "hello Matrix")

        then:
        1 * mockHttpClient.send({ java.net.http.HttpRequest req ->
            req.uri().toString().contains("/_matrix/client/v3/rooms/") &&
            req.uri().toString().contains("/send/m.room.message/") &&
            req.method() == "PUT"
        }, _) >> mockResponse

        eventId == "\$evt123"
    }

    def "sendMessage throws on non-2xx response"() {
        given:
        def mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 403
        mockHttpClient.send(_, _) >> mockResponse

        when:
        client.sendMessage("!room1:example.com", "hello")

        then:
        thrown(IOException)
    }

    def "constructor strips trailing slash from homeserver URL"() {
        given:
        def clientWithSlash = new MatrixApiClient("https://matrix.example.com/", "token", mockHttpClient)
        def syncJson = '{"next_batch": "s0", "rooms": {}}'
        def mockResponse = Mock(HttpResponse) {
            statusCode() >> 200
            body() >> syncJson
        }
        def capturedUri = null
        mockHttpClient.send(_, _) >> { args ->
            capturedUri = args[0].uri().toString()
            mockResponse
        }

        when:
        clientWithSlash.sync(null, 30000)

        then:
        capturedUri.startsWith("https://matrix.example.com/_matrix")
        !capturedUri.contains("com//_matrix")
    }
}
