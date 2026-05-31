package io.jaiclaw.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A complete conversation transcript for a session.
 *
 * @param sessionId   the session identifier
 * @param tenantId    the tenant that owns this session (nullable for single-tenant)
 * @param agentId     the agent that handled the conversation
 * @param channel     the channel the conversation occurred on (e.g. "telegram", "slack")
 * @param startTime   when the session began
 * @param utterances  ordered list of utterances in the conversation
 */
public record TranscriptSession(
        String sessionId,
        String tenantId,
        String agentId,
        String channel,
        Instant startTime,
        List<TranscriptUtterance> utterances
) {
    public TranscriptSession {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (startTime == null) startTime = Instant.now();
        if (utterances == null) utterances = List.of();
    }

    /**
     * Returns a new TranscriptSession with the utterance appended.
     */
    public TranscriptSession withUtterance(TranscriptUtterance utterance) {
        List<TranscriptUtterance> newUtterances = new ArrayList<>(utterances);
        newUtterances.add(utterance);
        return new TranscriptSession(sessionId, tenantId, agentId, channel, startTime,
                List.copyOf(newUtterances));
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String sessionId;
        private String tenantId;
        private String agentId;
        private String channel;
        private Instant startTime;
        private List<TranscriptUtterance> utterances;

        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder agentId(String agentId) { this.agentId = agentId; return this; }
        public Builder channel(String channel) { this.channel = channel; return this; }
        public Builder startTime(Instant startTime) { this.startTime = startTime; return this; }
        public Builder utterances(List<TranscriptUtterance> utterances) { this.utterances = utterances; return this; }

        public TranscriptSession build() {
            return new TranscriptSession(sessionId, tenantId, agentId, channel, startTime, utterances);
        }
    }
}
