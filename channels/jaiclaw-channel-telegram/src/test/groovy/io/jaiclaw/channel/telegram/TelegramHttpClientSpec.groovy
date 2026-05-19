package io.jaiclaw.channel.telegram

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Specification

class TelegramHttpClientSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    MockWebServer server

    def setup() {
        server = new MockWebServer()
        server.start()
    }

    def cleanup() {
        server.shutdown()
    }

    // --- GET JSON tests ---

    def "JDK get() returns parsed JSON"() {
        given:
        def json = '{"ok":true,"result":{"update_id":123}}'
        server.enqueue(new MockResponse()
                .setBody(json)
                .setHeader("Content-Type", "application/json"))
        def url = server.url("/botTOKEN/getUpdates").toString()

        when:
        def result = new JdkHttpClientTelegramHttpClient(5).get(url)

        then:
        result.path("ok").asBoolean()
        result.path("result").path("update_id").asInt() == 123
    }

    def "RestTemplate get() returns parsed JSON"() {
        given:
        def json = '{"ok":true,"result":{"update_id":123}}'
        server.enqueue(new MockResponse()
                .setBody(json)
                .setHeader("Content-Type", "application/json"))
        def url = server.url("/botTOKEN/getUpdates").toString()

        when:
        def result = new RestTemplateTelegramHttpClient(5).get(url)

        then:
        result.path("ok").asBoolean()
        result.path("result").path("update_id").asInt() == 123
    }

    def "JDK get() throws TelegramHttpException on HTTP error"() {
        given:
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody('{"ok":false,"description":"Not Found"}'))
        def url = server.url("/botTOKEN/badEndpoint").toString()

        when:
        new JdkHttpClientTelegramHttpClient(5).get(url)

        then:
        thrown(TelegramHttpException)
    }

    def "RestTemplate get() throws TelegramHttpException on HTTP error"() {
        given:
        server.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody('{"ok":false,"description":"Not Found"}'))
        def url = server.url("/botTOKEN/badEndpoint").toString()

        when:
        new RestTemplateTelegramHttpClient(5).get(url)

        then:
        thrown(TelegramHttpException)
    }

    // --- POST JSON tests ---

    def "JDK post() sends JSON body and parses response"() {
        given:
        def responseJson = '{"ok":true,"result":{"message_id":42}}'
        server.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"))
        def url = server.url("/botTOKEN/sendMessage").toString()
        def body = [chat_id: "222", text: "hello", parse_mode: "Markdown"]

        when:
        def result = new JdkHttpClientTelegramHttpClient(5).post(url, body)

        then:
        result.path("result").path("message_id").asInt() == 42

        and: "request body was valid JSON with correct fields"
        def request = server.takeRequest()
        request.getHeader("Content-Type").contains("application/json")
        def sentBody = MAPPER.readTree(request.body.readUtf8())
        sentBody.path("chat_id").asText() == "222"
        sentBody.path("text").asText() == "hello"
    }

    def "RestTemplate post() sends JSON body and parses response"() {
        given:
        def responseJson = '{"ok":true,"result":{"message_id":42}}'
        server.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"))
        def url = server.url("/botTOKEN/sendMessage").toString()
        def body = [chat_id: "222", text: "hello", parse_mode: "Markdown"]

        when:
        def result = new RestTemplateTelegramHttpClient(5).post(url, body)

        then:
        result.path("result").path("message_id").asInt() == 42

        and: "request body was valid JSON with correct fields"
        def request = server.takeRequest()
        request.getHeader("Content-Type").contains("application/json")
        def sentBody = MAPPER.readTree(request.body.readUtf8())
        sentBody.path("chat_id").asText() == "222"
        sentBody.path("text").asText() == "hello"
    }

    // --- GET bytes tests ---

    def "JDK getBytes() returns raw bytes"() {
        given:
        def data = "binary file content".bytes
        server.enqueue(new MockResponse()
                .setBody(new okio.Buffer().write(data))
                .setHeader("Content-Type", "application/octet-stream"))
        def url = server.url("/file/botTOKEN/documents/report.pdf").toString()

        when:
        def result = new JdkHttpClientTelegramHttpClient(5).getBytes(url)

        then:
        result == data
    }

    def "RestTemplate getBytes() returns raw bytes"() {
        given:
        def data = "binary file content".bytes
        server.enqueue(new MockResponse()
                .setBody(new okio.Buffer().write(data))
                .setHeader("Content-Type", "application/octet-stream"))
        def url = server.url("/file/botTOKEN/documents/report.pdf").toString()

        when:
        def result = new RestTemplateTelegramHttpClient(5).getBytes(url)

        then:
        result == data
    }

    def "JDK getBytes() throws on HTTP error"() {
        given:
        server.enqueue(new MockResponse().setResponseCode(500))
        def url = server.url("/file/botTOKEN/missing").toString()

        when:
        new JdkHttpClientTelegramHttpClient(5).getBytes(url)

        then:
        thrown(TelegramHttpException)
    }

    def "RestTemplate getBytes() throws on HTTP error"() {
        given:
        server.enqueue(new MockResponse().setResponseCode(500))
        def url = server.url("/file/botTOKEN/missing").toString()

        when:
        new RestTemplateTelegramHttpClient(5).getBytes(url)

        then:
        thrown(TelegramHttpException)
    }

    // --- POST multipart tests ---

    def "JDK postMultipart() sends multipart form data"() {
        given:
        def responseJson = '{"ok":true,"result":{"message_id":99}}'
        server.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"))
        def url = server.url("/botTOKEN/sendDocument").toString()
        def fileData = "PDF content".bytes
        def parts = new LinkedHashMap<String, Object>()
        parts.put("chat_id", "222")
        parts.put("document", new MultipartFile("report.pdf", fileData))

        when:
        def result = new JdkHttpClientTelegramHttpClient(5).postMultipart(url, parts)

        then:
        result.path("result").path("message_id").asInt() == 99

        and: "request was multipart"
        def request = server.takeRequest()
        request.getHeader("Content-Type").contains("multipart/form-data")
    }

    def "RestTemplate postMultipart() sends multipart form data"() {
        given:
        def responseJson = '{"ok":true,"result":{"message_id":99}}'
        server.enqueue(new MockResponse()
                .setBody(responseJson)
                .setHeader("Content-Type", "application/json"))
        def url = server.url("/botTOKEN/sendDocument").toString()
        def fileData = "PDF content".bytes
        def parts = new LinkedHashMap<String, Object>()
        parts.put("chat_id", "222")
        parts.put("document", new MultipartFile("report.pdf", fileData))

        when:
        def result = new RestTemplateTelegramHttpClient(5).postMultipart(url, parts)

        then:
        result.path("result").path("message_id").asInt() == 99

        and: "request was multipart"
        def request = server.takeRequest()
        request.getHeader("Content-Type").contains("multipart/form-data")
    }

    // --- TelegramHttpClientType enum tests ---

    def "TelegramHttpClientType.fromString parses valid values"() {
        expect:
        TelegramHttpClientType.fromString(input) == expected

        where:
        input             | expected
        "jdk"             | TelegramHttpClientType.JDK
        "JDK"             | TelegramHttpClientType.JDK
        "rest-template"   | TelegramHttpClientType.REST_TEMPLATE
        "rest_template"   | TelegramHttpClientType.REST_TEMPLATE
        "resttemplate"    | TelegramHttpClientType.REST_TEMPLATE
        "REST_TEMPLATE"   | TelegramHttpClientType.REST_TEMPLATE
        "web-client"      | TelegramHttpClientType.WEB_CLIENT
        "web_client"      | TelegramHttpClientType.WEB_CLIENT
        "webclient"       | TelegramHttpClientType.WEB_CLIENT
        "WEB_CLIENT"      | TelegramHttpClientType.WEB_CLIENT
        null              | TelegramHttpClientType.JDK
        ""                | TelegramHttpClientType.JDK
        "  "              | TelegramHttpClientType.JDK
        "unknown"         | TelegramHttpClientType.JDK
    }
}
