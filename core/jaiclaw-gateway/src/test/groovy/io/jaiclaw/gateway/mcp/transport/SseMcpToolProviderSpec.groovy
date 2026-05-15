package io.jaiclaw.gateway.mcp.transport

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Specification

class SseMcpToolProviderSpec extends Specification {

    static final ObjectMapper mapper = new ObjectMapper()

    def "returns configured server name and description"() {
        given:
        def transport = Stub(SseHttpTransport)
        def provider = new SseMcpToolProvider("cal-server", "Calendar", "http://localhost:8080/sse", transport)

        expect:
        provider.getServerName() == "cal-server"
        provider.getServerDescription() == "Calendar"
    }

    def "uses server name as description when description is null"() {
        given:
        def transport = Stub(SseHttpTransport)
        def provider = new SseMcpToolProvider("cal-server", null, "http://localhost:8080/sse", transport)

        expect:
        provider.getServerDescription() == "cal-server"
    }

    def "returns empty tool list before connect"() {
        given:
        def transport = Stub(SseHttpTransport)
        def provider = new SseMcpToolProvider("test", "Test", "http://localhost:8080/sse", transport)

        expect:
        provider.getTools().isEmpty()
    }

    def "connect sends initialize, notifications/initialized, then tools/list in order"() {
        given: "a piped SSE stream where the mock transport writes responses on POST"
        def postedBodies = Collections.synchronizedList(new ArrayList<String>())

        // Use piped streams so the SSE reader thread blocks until data is written
        def pipedOut = new PipedOutputStream()
        def pipedIn = new PipedInputStream(pipedOut)

        // Write the endpoint event immediately so connect() can discover the POST URL
        def endpointEvent = "event:endpoint\ndata:/mcp/message\n\n"
        pipedOut.write(endpointEvent.getBytes("UTF-8"))
        pipedOut.flush()

        def transport = Mock(SseHttpTransport) {
            connectSseStream("http://localhost:8080/sse") >> pipedIn
            postJsonRpc(_, _) >> { String url, String json ->
                postedBodies.add(json)
                def parsed = mapper.readTree(json)

                if (parsed.has("id")) {
                    // It's a request — write a JSON-RPC response on the SSE stream
                    int id = parsed.get("id").asInt()
                    def method = parsed.get("method").asText()

                    Map resultPayload
                    if (method == "initialize") {
                        resultPayload = [
                                protocolVersion: "2024-11-05",
                                capabilities   : [:],
                                serverInfo     : [name: "test-server", version: "1.0"]
                        ]
                    } else if (method == "tools/list") {
                        resultPayload = [
                                tools: [
                                        [name: "echo", description: "Echo tool", inputSchema: [type: "object"]]
                                ]
                        ]
                    } else {
                        resultPayload = [:]
                    }

                    def responseJson = mapper.writeValueAsString([jsonrpc: "2.0", id: id, result: resultPayload])
                    def sseEvent = "event:message\ndata:${responseJson}\n\n"
                    pipedOut.write(sseEvent.getBytes("UTF-8"))
                    pipedOut.flush()
                    return 202
                } else {
                    // It's a notification — no SSE response needed
                    return 204
                }
            }
        }

        def provider = new SseMcpToolProvider("test-server", "Test", "http://localhost:8080/sse", transport)

        when:
        provider.connect()

        then: "three POST requests were sent"
        postedBodies.size() == 3

        and: "first POST is initialize (has id, method=initialize)"
        def first = mapper.readTree(postedBodies[0])
        first.get("jsonrpc").asText() == "2.0"
        first.has("id")
        first.get("method").asText() == "initialize"

        and: "second POST is notifications/initialized (no id)"
        def second = mapper.readTree(postedBodies[1])
        second.get("jsonrpc").asText() == "2.0"
        !second.has("id")
        second.get("method").asText() == "notifications/initialized"

        and: "third POST is tools/list (has id)"
        def third = mapper.readTree(postedBodies[2])
        third.get("jsonrpc").asText() == "2.0"
        third.has("id")
        third.get("method").asText() == "tools/list"

        and: "tools were cached"
        provider.getTools().size() == 1
        provider.getTools()[0].name() == "echo"

        cleanup:
        provider.destroy()
        pipedOut.close()
    }

    def "destroy completes pending requests with error"() {
        given:
        def transport = Mock(SseHttpTransport)
        def provider = new SseMcpToolProvider("test", "Test", "http://localhost:8080/sse", transport)

        when:
        provider.destroy()

        then:
        noExceptionThrown()
    }
}
