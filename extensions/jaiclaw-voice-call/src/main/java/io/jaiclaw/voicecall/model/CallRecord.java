package io.jaiclaw.voicecall.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.*;

/**
 * Mutable state of a voice call. Updated during event processing and persisted to the call store.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallRecord {

    private String callId;
    private String providerCallId;
    private String provider;
    private String tenantId;   // tenant that originated/owns the call; set at registration time
    private CallDirection direction;
    private CallState state;
    private String from;
    private String to;
    private String sessionKey;
    private CallMode mode;
    private Instant startedAt;
    private Instant answeredAt;
    private Instant endedAt;
    private EndReason endReason;
    private List<TranscriptEntry> transcript = new ArrayList<>();
    private Set<String> processedEventIds = new HashSet<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public CallRecord() {}

    public CallRecord(String callId, String provider, CallDirection direction,
                      String from, String to, CallMode mode) {
        this.callId = callId;
        this.provider = provider;
        this.direction = direction;
        this.from = from;
        this.to = to;
        this.mode = mode;
        this.state = CallState.INITIATED;
        this.startedAt = Instant.now();
    }

    // --- Getters and setters ---

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getProviderCallId() { return providerCallId; }
    public void setProviderCallId(String providerCallId) { this.providerCallId = providerCallId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public CallDirection getDirection() { return direction; }
    public void setDirection(CallDirection direction) { this.direction = direction; }

    public CallState getState() { return state; }
    public void setState(CallState state) { this.state = state; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public CallMode getMode() { return mode; }
    public void setMode(CallMode mode) { this.mode = mode; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public EndReason getEndReason() { return endReason; }
    public void setEndReason(EndReason endReason) { this.endReason = endReason; }

    public List<TranscriptEntry> getTranscript() { return transcript; }
    public void setTranscript(List<TranscriptEntry> transcript) { this.transcript = transcript; }

    public Set<String> getProcessedEventIds() { return processedEventIds; }
    public void setProcessedEventIds(Set<String> processedEventIds) { this.processedEventIds = processedEventIds; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    /**
     * Adds a transcript entry for the given speaker.
     */
    public void addTranscriptEntry(TranscriptEntry.Speaker speaker, String text) {
        transcript.add(new TranscriptEntry(Instant.now(), speaker, text, true));
    }

    /**
     * Returns true if this event ID has already been processed.
     */
    public boolean hasProcessedEvent(String eventId) {
        return processedEventIds.contains(eventId);
    }

    /**
     * Marks an event ID as processed.
     */
    public void markEventProcessed(String eventId) {
        processedEventIds.add(eventId);
    }

    @Override
    public String toString() {
        return "CallRecord{callId='%s', state=%s, direction=%s, from='%s', to='%s'}"
                .formatted(callId, state, direction, from, to);
    }
}
