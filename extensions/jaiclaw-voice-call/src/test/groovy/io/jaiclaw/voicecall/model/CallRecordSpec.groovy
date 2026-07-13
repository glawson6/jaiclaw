package io.jaiclaw.voicecall.model

import tools.jackson.databind.ObjectMapper
import spock.lang.Specification

class CallRecordSpec extends Specification {

    ObjectMapper objectMapper = new ObjectMapper()

    def "CallRecord Jackson round-trip serialization"() {
        given:
        def call = new CallRecord("call-123", "twilio", CallDirection.OUTBOUND,
                "+15551234567", "+15557654321", CallMode.CONVERSATION)
        call.providerCallId = "CA123"
        call.sessionKey = "session-abc"
        call.addTranscriptEntry(TranscriptEntry.Speaker.BOT, "Hello!")
        call.addTranscriptEntry(TranscriptEntry.Speaker.USER, "Hi there")

        when:
        def json = objectMapper.writeValueAsString(call)
        def deserialized = objectMapper.readValue(json, CallRecord)

        then:
        deserialized.callId == "call-123"
        deserialized.providerCallId == "CA123"
        deserialized.provider == "twilio"
        deserialized.direction == CallDirection.OUTBOUND
        deserialized.state == CallState.INITIATED
        deserialized.from == "+15551234567"
        deserialized.to == "+15557654321"
        deserialized.mode == CallMode.CONVERSATION
        deserialized.transcript.size() == 2
        deserialized.transcript[0].speaker() == TranscriptEntry.Speaker.BOT
        deserialized.transcript[0].text() == "Hello!"
        deserialized.transcript[1].speaker() == TranscriptEntry.Speaker.USER
        deserialized.transcript[1].text() == "Hi there"
    }

    def "CallRecord event dedup tracking"() {
        given:
        def call = new CallRecord("call-1", "twilio", CallDirection.INBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)

        expect:
        !call.hasProcessedEvent("evt-1")

        when:
        call.markEventProcessed("evt-1")

        then:
        call.hasProcessedEvent("evt-1")
        !call.hasProcessedEvent("evt-2")
    }

    def "CallRecord initial state is INITIATED"() {
        given:
        def call = new CallRecord("call-1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.NOTIFY)

        expect:
        call.state == CallState.INITIATED
        call.startedAt != null
        call.transcript.isEmpty()
        call.processedEventIds.isEmpty()
    }

    def "CallRecord deserialization ignores unknown properties"() {
        given:
        def json = '{"callId":"c1","state":"ACTIVE","unknownField":"value","direction":"INBOUND"}'

        when:
        def call = objectMapper.readValue(json, CallRecord)

        then:
        call.callId == "c1"
        call.state == CallState.ACTIVE
        call.direction == CallDirection.INBOUND
    }
}
