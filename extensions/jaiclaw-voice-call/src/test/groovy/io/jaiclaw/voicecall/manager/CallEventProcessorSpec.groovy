package io.jaiclaw.voicecall.manager

import io.jaiclaw.voicecall.model.*
import io.jaiclaw.voicecall.store.InMemoryCallStore
import io.jaiclaw.voicecall.config.VoiceCallProperties
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class CallEventProcessorSpec extends Specification {

    def activeCalls = new ConcurrentHashMap<String, CallRecord>()
    def providerCallIdMap = new ConcurrentHashMap<String, String>()
    def callStore = new InMemoryCallStore()
    def answeredCalls = []
    def inboundPolicy = new InboundPolicy(
            new VoiceCallProperties.InboundProperties("open", null, null, []))

    def processor = new CallEventProcessor(
            activeCalls, providerCallIdMap, callStore, inboundPolicy,
            { call -> answeredCalls << call })

    def "processes call initiated event and auto-registers inbound call"() {
        given:
        def event = new NormalizedEvent.CallInitiated(
                "evt-1", "dedup-1", "call-1", "CA123", Instant.now(),
                "+15551234567", "+15557654321", CallDirection.INBOUND)

        when:
        def handled = processor.processEvent(event)

        then:
        handled
        // Keys are tenant-scoped now ("default:call-1" in SINGLE mode).
        activeCalls.containsKey("default:call-1")
        activeCalls["default:call-1"].direction == CallDirection.INBOUND
        activeCalls["default:call-1"].providerCallId == "CA123"
        providerCallIdMap["CA123"] == "default:call-1"
    }

    def "deduplicates events by dedupeKey"() {
        given:
        def event1 = new NormalizedEvent.CallInitiated(
                "evt-1", "dedup-1", "call-1", "CA123", Instant.now(),
                "+1555", "+1666", CallDirection.INBOUND)
        def event2 = new NormalizedEvent.CallInitiated(
                "evt-2", "dedup-1", "call-1", "CA123", Instant.now(),
                "+1555", "+1666", CallDirection.INBOUND)

        when:
        def handled1 = processor.processEvent(event1)
        def handled2 = processor.processEvent(event2)

        then:
        handled1
        !handled2 // Deduplicated
    }

    def "transitions through call lifecycle on events"() {
        given:
        def call = new CallRecord("call-1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
        call.providerCallId = "CA123"
        activeCalls["call-1"] = call
        providerCallIdMap["CA123"] = "call-1"

        when: "call is answered"
        processor.processEvent(new NormalizedEvent.CallAnswered(
                "evt-1", "dedup-1", "call-1", "CA123", Instant.now()))

        then:
        call.state == CallState.ANSWERED
        call.answeredAt != null
        answeredCalls.size() == 1

        when: "speech received"
        processor.processEvent(new NormalizedEvent.CallSpeech(
                "evt-2", "dedup-2", "call-1", "CA123", Instant.now(),
                "Hello there", true, 0.95))

        then:
        call.transcript.size() == 1
        call.transcript[0].text() == "Hello there"
        call.transcript[0].speaker() == TranscriptEntry.Speaker.USER
    }

    def "finalizes call on ended event"() {
        given:
        def call = new CallRecord("call-1", "twilio", CallDirection.OUTBOUND,
                "+1555", "+1666", CallMode.CONVERSATION)
        call.state = CallState.ACTIVE
        call.tenantId = "default"
        call.providerCallId = "CA123"
        activeCalls["default:call-1"] = call
        providerCallIdMap["CA123"] = "default:call-1"

        when:
        processor.processEvent(new NormalizedEvent.CallEnded(
                "evt-1", "dedup-1", "call-1", "CA123", Instant.now(),
                EndReason.USER_HANGUP))

        then:
        call.state == CallState.HANGUP_USER
        call.endReason == EndReason.USER_HANGUP
        call.endedAt != null
        !activeCalls.containsKey("default:call-1")
        !providerCallIdMap.containsKey("CA123")
    }

    def "rejects inbound call when policy is disabled"() {
        given:
        def restrictedPolicy = new InboundPolicy(
                new VoiceCallProperties.InboundProperties("disabled", null, null, []))
        def restrictedProcessor = new CallEventProcessor(
                activeCalls, providerCallIdMap, callStore, restrictedPolicy, null)

        def event = new NormalizedEvent.CallInitiated(
                "evt-1", "dedup-1", "call-1", "CA123", Instant.now(),
                "+1555", "+1666", CallDirection.INBOUND)

        when:
        def handled = restrictedProcessor.processEvent(event)

        then:
        !handled
        !activeCalls.containsKey("call-1")
    }
}
