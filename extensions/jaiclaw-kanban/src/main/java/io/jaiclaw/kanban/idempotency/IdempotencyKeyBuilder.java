package io.jaiclaw.kanban.idempotency;

import io.jaiclaw.kanban.service.TransitionHistory;
import io.jaiclaw.tasks.TaskRecord;

/**
 * Produces the stable idempotency key called for in analysis §6.8:
 * {@code {boardId}:{taskId}:{state}:{entrySeq}} where {@code entrySeq} is
 * the count of prior entries into this state for this task (taken from
 * {@link TransitionHistory}).
 *
 * <p>The key is identical across crash-retries of the same unit of work
 * (a card that died mid-execution in {@code drafting} keeps the same key
 * on resume), but changes when the card legitimately re-enters the same
 * state via a different path (a {@code REJECT} from {@code review} back
 * into {@code drafting} bumps {@code entrySeq}, producing a new key).
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code AgentColumnProcessor} as the
 *       {@link EffectLedger} lookup key — read-before-execute on retry</li>
 *   <li>The prompt template ({@code {{idempotencyKey}}}) so the agent
 *       sees the key in-band and can branch its tool calls on it</li>
 * </ul>
 */
public final class IdempotencyKeyBuilder {

    private final TransitionHistory history;

    public IdempotencyKeyBuilder(TransitionHistory history) {
        this.history = history;
    }

    /**
     * Build the key for the *current* attempt — the entrySeq counts prior
     * entries *plus* the one represented by the card now being in
     * {@code state}, so the key is stable across retries of the same
     * entry.
     */
    public String build(TaskRecord card) {
        if (card == null || card.boardId() == null || card.state() == null) {
            throw new IllegalArgumentException(
                    "idempotency key requires a card with boardId and state");
        }
        int entrySeq = Math.max(1, history.entrySeq(card.id(), card.state()));
        return card.boardId() + ":" + card.id() + ":" + card.state() + ":" + entrySeq;
    }
}
