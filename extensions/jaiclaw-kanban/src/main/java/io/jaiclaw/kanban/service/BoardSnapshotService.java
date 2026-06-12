package io.jaiclaw.kanban.service;

import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.BoardSnapshot;
import io.jaiclaw.kanban.model.CardView;
import io.jaiclaw.kanban.model.ColumnDefinition;
import io.jaiclaw.kanban.state.TaskStateEngine;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a point-in-time {@link BoardSnapshot} from the live {@link TaskStore}
 * for the dashboard REST API, the SSE snapshot-on-connect, the ASCII
 * renderer, and the Actuator endpoint.
 *
 * <p>Reads are tenant-scoped through the existing {@link KanbanBoardService}
 * + {@link TaskStore} contracts — this service does no tenant resolution of
 * its own.
 */
public class BoardSnapshotService {

    private final KanbanBoardService boardService;
    private final TaskStore taskStore;
    private final TaskStateEngine engine;

    public BoardSnapshotService(KanbanBoardService boardService,
                                TaskStore taskStore,
                                TaskStateEngine engine) {
        this.boardService = boardService;
        this.taskStore = taskStore;
        this.engine = engine;
    }

    /** Build a snapshot, or empty if the board id isn't visible to the current tenant. */
    public Optional<BoardSnapshot> snapshot(String boardId) {
        return boardService.get(boardId).map(this::snapshotOf);
    }

    public BoardSnapshot snapshotOf(BoardDefinition board) {
        List<BoardSnapshot.ColumnSnapshot> columns = new ArrayList<>(board.columns().size());
        int total = 0;
        for (ColumnDefinition col : board.columns()) {
            List<TaskRecord> cards = taskStore.findByBoardAndState(board.id(), col.state());
            List<CardView> views = cards.stream()
                    .map(t -> CardView.from(t, allowedEventsFor(board, t)))
                    .toList();
            total += views.size();
            columns.add(new BoardSnapshot.ColumnSnapshot(
                    col.state(), col.name(), col.wipLimit(), col.terminal(), views));
        }
        return new BoardSnapshot(board.id(), board.name(), null, columns, total);
    }

    /** Allowed transition events from the card's current state. */
    public List<String> allowedEventsFor(BoardDefinition board, TaskRecord card) {
        return engine.allowedEvents(board, card.state());
    }
}
