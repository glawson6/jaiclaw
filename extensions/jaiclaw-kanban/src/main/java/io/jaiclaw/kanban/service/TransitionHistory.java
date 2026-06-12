package io.jaiclaw.kanban.service;

import io.jaiclaw.kanban.model.TransitionRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded per-board ring buffer of accepted transitions plus a by-task index
 * for quick lookup. Mirrors {@code PipelineExecutionTracker} from
 * {@code jaiclaw-pipeline}.
 *
 * <p>Used by the Phase 2 read APIs ({@code GET …/boards/{id}/history},
 * Actuator endpoint) and by Phase 3's idempotency-key derivation (counts
 * prior entries into a state per analysis §6.8). Phase 4 backs this with an
 * optional append-only jsonl journal so the deque becomes a cache over the
 * tail.
 */
public class TransitionHistory {

    private final int maxPerBoard;
    private final Map<String, Deque<TransitionRecord>> byBoard = new HashMap<>();
    private final Map<String, List<TransitionRecord>> byTask = new HashMap<>();

    public TransitionHistory(int maxPerBoard) {
        this.maxPerBoard = Math.max(1, maxPerBoard);
    }

    public synchronized void record(TransitionRecord record) {
        Deque<TransitionRecord> deque = byBoard.computeIfAbsent(
                record.boardId(), k -> new ArrayDeque<>(maxPerBoard));
        if (deque.size() >= maxPerBoard) {
            deque.pollFirst();
        }
        deque.addLast(record);
        byTask.computeIfAbsent(record.taskId(), k -> new ArrayList<>()).add(record);
    }

    public synchronized List<TransitionRecord> forBoard(String boardId, int limit) {
        Deque<TransitionRecord> deque = byBoard.get(boardId);
        if (deque == null) return List.of();
        return deque.stream()
                .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
                .limit(limit > 0 ? limit : deque.size())
                .toList();
    }

    public synchronized List<TransitionRecord> forTask(String taskId) {
        List<TransitionRecord> list = byTask.get(taskId);
        return list != null ? List.copyOf(list) : List.of();
    }

    /**
     * Count of prior entries into the given state on the given board for the
     * given task. Used to derive Phase 3 idempotency keys without coupling
     * to the journal yet.
     */
    public synchronized int entrySeq(String taskId, String state) {
        List<TransitionRecord> list = byTask.get(taskId);
        if (list == null) return 0;
        int count = 0;
        for (TransitionRecord r : list) {
            if (state.equals(r.toState())) count++;
        }
        return count;
    }

    /** Drop all history for a board (e.g. after delete-board). Test hook. */
    public synchronized void clear(String boardId) {
        Deque<TransitionRecord> deque = byBoard.remove(boardId);
        if (deque == null) return;
        for (TransitionRecord r : deque) {
            List<TransitionRecord> list = byTask.get(r.taskId());
            if (list != null) list.removeIf(x -> boardId.equals(x.boardId()));
        }
    }
}
