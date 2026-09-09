package io.jaiclaw.learning.review;

import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.core.model.AssistantMessage;
import io.jaiclaw.core.model.Message;
import io.jaiclaw.core.model.Session;
import io.jaiclaw.core.model.SystemMessage;
import io.jaiclaw.core.model.ToolResultMessage;
import io.jaiclaw.core.model.UserMessage;

import java.util.List;
import java.util.Optional;

/**
 * Renders a session's messages as the plain-text transcript the reviewer reads.
 *
 * <p><strong>Read-only by construction.</strong> This is the only place the
 * learning module touches a live session, and it takes a defensive copy of the
 * message list without calling any mutator. The cache-safety invariant — that a
 * review never alters the session or its prompt — is enforced here and asserted
 * in {@code LearningLoopE2ESpec}.
 *
 * <p>System messages are excluded: they are the agent's own instructions, and
 * feeding them back to a reviewer invites it to "learn" what it was already told.
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public class TranscriptSourceAdapter {

    private final SessionManager sessionManager;

    public TranscriptSourceAdapter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /** How many messages the session holds; used by the cadence gate. */
    public int messageCount(String sessionKey) {
        return sessionManager.get(sessionKey).map(s -> s.messages().size()).orElse(0);
    }

    /**
     * The transcript, or empty when the session is unknown or has nothing to show.
     */
    public Optional<String> transcript(String sessionKey) {
        Optional<Session> session = sessionManager.get(sessionKey);
        if (session.isEmpty()) return Optional.empty();

        List<Message> messages = List.copyOf(session.get().messages());
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            String line = render(m);
            if (line != null) sb.append(line).append('\n');
        }
        String text = sb.toString().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private String render(Message m) {
        return switch (m) {
            case UserMessage u -> "user: " + safe(u.content());
            case AssistantMessage a -> "assistant: " + safe(a.content());
            case ToolResultMessage t -> "tool[" + t.toolName() + "]: " + safe(t.content());
            // Excluded on purpose — see the class javadoc.
            case SystemMessage ignored -> null;
        };
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
