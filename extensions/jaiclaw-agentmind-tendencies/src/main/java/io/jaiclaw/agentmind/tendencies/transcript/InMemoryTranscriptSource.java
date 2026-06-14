package io.jaiclaw.agentmind.tendencies.transcript;

import io.jaiclaw.core.hook.event.MessageReceivedEvent;
import io.jaiclaw.core.hook.event.SessionEndedEvent;
import io.jaiclaw.core.plugin.PluginDefinition;
import io.jaiclaw.core.plugin.PluginKind;
import io.jaiclaw.plugin.JaiClawPlugin;
import io.jaiclaw.plugin.PluginApi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-process {@link TranscriptSource}. Implemented as a
 * {@link JaiClawPlugin} so it can subscribe to {@code MessageReceivedEvent}
 * to record per-session transcripts as they arrive, and to
 * {@code SessionEndedEvent} to evict them once the session closes.
 *
 * <p>Bounded per session: only the most-recent {@code maxMessagesPerSession}
 * user messages are retained (older messages roll off). This bounds memory
 * even for long-running sessions.
 *
 * <p>Listens at a higher priority (200) than the
 * {@code TendenciesDialecticTrigger} (default 500) so the transcript is
 * populated before the trigger fires on {@code SessionEndedEvent}.
 *
 * <p>Plan §8 task 3.9.
 */
public class InMemoryTranscriptSource implements TranscriptSource, JaiClawPlugin {

    private final ConcurrentHashMap<String, Deque<String>> transcripts = new ConcurrentHashMap<>();
    private final int maxMessagesPerSession;

    public InMemoryTranscriptSource() {
        this(100);
    }

    public InMemoryTranscriptSource(int maxMessagesPerSession) {
        if (maxMessagesPerSession < 1) {
            throw new IllegalArgumentException("maxMessagesPerSession must be >= 1");
        }
        this.maxMessagesPerSession = maxMessagesPerSession;
    }

    @Override
    public List<String> recentMessages(String sessionKey) {
        Deque<String> deque = transcripts.get(sessionKey);
        return deque == null ? List.of() : new ArrayList<>(deque);
    }

    /** Test / SessionEndedEvent helper. */
    public void clear(String sessionKey) {
        transcripts.remove(sessionKey);
    }

    @Override
    public PluginDefinition definition() {
        return PluginDefinition.builder()
                .id("agentmind-tendencies-transcript-source")
                .name("AgentMind Tendencies In-Memory Transcript Source")
                .description("Records bounded per-session transcripts on MessageReceivedEvent; "
                        + "evicts on SessionEndedEvent.")
                .version("1.0.0")
                .kind(PluginKind.GENERAL)
                .build();
    }

    @Override
    public void register(PluginApi api) {
        // Priority 200 so transcripts populate before the dialectic
        // trigger (priority 500) reads them on SessionEndedEvent.
        api.on(MessageReceivedEvent.class, event -> {
            record(event.sessionKey(), event.content());
            return null;
        }, 200);
        api.on(SessionEndedEvent.class, event -> {
            // Eviction at priority 1000, AFTER the dialectic trigger
            // (priority 500) has read the transcript on the same event.
            clear(event.sessionKey());
            return null;
        }, 1000);
    }

    void record(String sessionKey, String content) {
        if (sessionKey == null || content == null) return;
        transcripts.computeIfAbsent(sessionKey, k -> new ArrayDeque<>()).add(content);
        Deque<String> dq = transcripts.get(sessionKey);
        synchronized (dq) {
            while (dq.size() > maxMessagesPerSession) {
                dq.pollFirst();
            }
        }
    }
}
