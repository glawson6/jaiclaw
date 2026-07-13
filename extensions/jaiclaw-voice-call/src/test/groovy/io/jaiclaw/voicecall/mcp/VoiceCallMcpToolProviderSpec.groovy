package io.jaiclaw.voicecall.mcp

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.voicecall.manager.CallManager
import io.jaiclaw.voicecall.model.*
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class VoiceCallMcpToolProviderSpec extends Specification {

    def callManager = Mock(CallManager)
    def objectMapper = new ObjectMapper()
    def provider = new VoiceCallMcpToolProvider(callManager, objectMapper)

    def "provides correct server name and description"() {
        expect:
        provider.serverName == "voice-call"
        provider.serverDescription.contains("Voice call")
    }

    def "provides 5 tool definitions"() {
        when:
        def tools = provider.tools

        then:
        tools.size() == 5
        tools*.name() == [
                "voice_call_initiate",
                "voice_call_continue",
                "voice_call_speak",
                "voice_call_end",
                "voice_call_status"
        ]
    }

    def "tool schemas are valid JSON"() {
        when:
        def tools = provider.tools

        then:
        tools.each { tool ->
            def schema = objectMapper.readTree(tool.inputSchema())
            assert schema.path("type").asText() == "object"
            assert schema.has("properties")
        }
    }

    def "voice_call_status returns call info"() {
        given:
        def call = new CallRecord("call-1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
        call.state = CallState.ACTIVE
        call.addTranscriptEntry(TranscriptEntry.Speaker.BOT, "Hello")
        callManager.getCall("call-1") >> Optional.of(call)

        when:
        def result = provider.execute("voice_call_status", [callId: "call-1"], null)

        then:
        !result.isError()
        def json = objectMapper.readTree(result.content())
        json.path("callId").asText() == "call-1"
        json.path("state").asText() == "active"
        json.path("isTerminal").asBoolean() == false
        json.path("transcript").size() == 1
    }

    def "voice_call_end handles non-existent call gracefully"() {
        given:
        callManager.endCall("nonexistent") >> CompletableFuture.completedFuture(null)

        when:
        def result = provider.execute("voice_call_end", [callId: "nonexistent"], null)

        then:
        !result.isError()
    }

    def "returns error for unknown tool"() {
        when:
        def result = provider.execute("unknown_tool", [:], null)

        then:
        result.isError()
        result.content().contains("Unknown tool")
    }

    def "returns error for missing required parameter"() {
        when:
        def result = provider.execute("voice_call_initiate", [:], null)

        then:
        result.isError()
        result.content().contains("Missing required parameter")
    }
}
