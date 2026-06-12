package io.jaiclaw.kanban.web;

import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.model.BoardSnapshot;
import io.jaiclaw.kanban.model.CardView;
import io.jaiclaw.kanban.model.TransitionRecord;
import io.jaiclaw.kanban.render.AsciiBoardOptions;
import io.jaiclaw.kanban.render.BoardAsciiRenderer;
import io.jaiclaw.kanban.service.BoardSnapshotService;
import io.jaiclaw.kanban.service.BoardWriteException;
import io.jaiclaw.kanban.service.KanbanBoardService;
import io.jaiclaw.kanban.service.TaskTransitionService;
import io.jaiclaw.kanban.service.TransitionHistory;
import io.jaiclaw.kanban.state.TransitionResult;
import io.jaiclaw.kanban.validation.BoardValidator;
import io.jaiclaw.tasks.TaskRecord;
import io.jaiclaw.tasks.TaskStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * REST surface for the dashboard.
 *
 * <p>Base path is configurable via {@code jaiclaw.kanban.http.base-path}
 * (default {@code /api/kanban}). Auth/AuthZ are delegated to the Spring
 * Security filter chain; this controller does no in-method checks.
 *
 * <p>Write endpoints ({@code POST /boards}, {@code DELETE /boards/{id}})
 * are gated by {@code jaiclaw.kanban.boards.writable} (analysis §9 Q1
 * resolution) — when false they return {@code 405 Method Not Allowed}.
 * Card-level writes ({@code POST /tasks…}) are not gated and remain
 * available regardless.
 */
@RestController
@RequestMapping("${jaiclaw.kanban.http.base-path:/api/kanban}")
public class KanbanBoardController {

    private final KanbanBoardService boardService;
    private final BoardSnapshotService snapshotService;
    private final TaskTransitionService transitionService;
    private final TransitionHistory history;
    private final TaskStore taskStore;
    private final BoardValidator validator;
    private final BoardAsciiRenderer renderer;

    public KanbanBoardController(
            KanbanBoardService boardService,
            BoardSnapshotService snapshotService,
            TaskTransitionService transitionService,
            TransitionHistory history,
            TaskStore taskStore,
            BoardValidator validator,
            BoardAsciiRenderer renderer) {
        this.boardService = boardService;
        this.snapshotService = snapshotService;
        this.transitionService = transitionService;
        this.history = history;
        this.taskStore = taskStore;
        this.validator = validator;
        this.renderer = renderer;
    }

    // ── Board reads ─────────────────────────────────────────────────

    @GetMapping("/boards")
    public ResponseEntity<List<Dtos.BoardSummary>> listBoards() {
        return ResponseEntity.ok(boardService.list().stream()
                .map(Dtos.BoardSummary::of)
                .toList());
    }

    @GetMapping("/boards/{boardId}")
    public ResponseEntity<?> getBoard(@PathVariable("boardId") String boardId) {
        return boardService.get(boardId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Dtos.ErrorBody.of("board not found: " + boardId)));
    }

    @GetMapping("/boards/{boardId}/snapshot")
    public ResponseEntity<?> snapshot(@PathVariable("boardId") String boardId) {
        Optional<BoardSnapshot> snap = snapshotService.snapshot(boardId);
        return snap.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Dtos.ErrorBody.of("board not found: " + boardId)));
    }

    @GetMapping("/boards/{boardId}/history")
    public ResponseEntity<?> history(
            @PathVariable("boardId") String boardId,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
        if (boardService.get(boardId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Dtos.ErrorBody.of("board not found: " + boardId));
        }
        List<TransitionRecord> records = history.forBoard(boardId, limit);
        return ResponseEntity.ok(records);
    }

    @GetMapping(value = "/boards/{boardId}/ascii", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> ascii(
            @PathVariable("boardId") String boardId,
            @RequestParam(value = "width", required = false, defaultValue = "120") int width,
            @RequestParam(value = "style", required = false, defaultValue = "full") String style) {
        Optional<BoardSnapshot> snap = snapshotService.snapshot(boardId);
        if (snap.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("board not found: " + boardId);
        }
        AsciiBoardOptions.Style parsedStyle = "compact".equalsIgnoreCase(style)
                ? AsciiBoardOptions.Style.COMPACT
                : AsciiBoardOptions.Style.FULL;
        AsciiBoardOptions options = new AsciiBoardOptions(width, parsedStyle, 2, true, "(empty)");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(renderer.render(snap.get(), options));
    }

    // ── Board writes (gated on boards.writable) ────────────────────

    @PostMapping("/boards")
    public ResponseEntity<?> createBoard(@RequestBody BoardDefinition definition) {
        if (definition == null) {
            return ResponseEntity.badRequest().body(Dtos.ErrorBody.of("body is required"));
        }
        try {
            validator.validateOrThrow(List.of(definition));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Dtos.ErrorBody.of("invalid board", ex.getMessage()));
        }
        try {
            boardService.register(definition);
        } catch (BoardWriteException ex) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(Dtos.ErrorBody.of(ex.getMessage()));
        }
        return ResponseEntity.created(java.net.URI.create(
                "/api/kanban/boards/" + definition.id())).body(definition);
    }

    @DeleteMapping("/boards/{boardId}")
    public ResponseEntity<?> deleteBoard(@PathVariable("boardId") String boardId) {
        try {
            boolean removed = boardService.remove(boardId);
            return removed
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Dtos.ErrorBody.of("board not found: " + boardId));
        } catch (BoardWriteException ex) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(Dtos.ErrorBody.of(ex.getMessage()));
        }
    }

    // ── Card / task endpoints ──────────────────────────────────────

    @PostMapping("/boards/{boardId}/tasks")
    public ResponseEntity<?> createCard(
            @PathVariable("boardId") String boardId,
            @RequestBody Dtos.CreateCardRequest body) {
        if (body == null || body.name() == null || body.name().isBlank()) {
            return ResponseEntity.badRequest().body(Dtos.ErrorBody.of("name is required"));
        }
        try {
            TaskRecord card = transitionService.createCard(
                    boardId, body.name(), body.description(), body.metadata());
            return ResponseEntity.status(HttpStatus.CREATED).body(toCardView(card));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Dtos.ErrorBody.of(ex.getMessage()));
        }
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getCard(@PathVariable("taskId") String taskId) {
        Optional<TaskRecord> task = taskStore.findById(taskId);
        if (task.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Dtos.ErrorBody.of("task not found: " + taskId));
        }
        return ResponseEntity.ok(toCardView(task.get()));
    }

    @PostMapping("/tasks/{taskId}/transition")
    public ResponseEntity<?> transition(
            @PathVariable("taskId") String taskId,
            @RequestBody Dtos.TransitionRequest body) {
        if (body == null || body.event() == null || body.event().isBlank()) {
            return ResponseEntity.badRequest().body(Dtos.ErrorBody.of("event is required"));
        }
        try {
            TransitionResult result = transitionService.transition(taskId, body.event(), body.actor());
            if (!result.accepted()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Dtos.ErrorBody.of("transition rejected", result.reason()));
            }
            return ResponseEntity.ok(history.forTask(taskId).stream()
                    .reduce((first, second) -> second)
                    .orElse(null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Dtos.ErrorBody.of(ex.getMessage()));
        }
    }

    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<?> claim(
            @PathVariable("taskId") String taskId,
            @RequestBody Dtos.ClaimRequest body) {
        Optional<TaskRecord> existing = taskStore.findById(taskId);
        if (existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Dtos.ErrorBody.of("task not found: " + taskId));
        }
        String assignee = body != null ? body.assignee() : null;
        TaskRecord claimed = existing.get().withAssignee(assignee);
        Optional<TaskRecord> persisted = taskStore.compareAndSave(claimed);
        if (persisted.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Dtos.ErrorBody.of("concurrent modification — re-read and retry"));
        }
        return ResponseEntity.ok(toCardView(persisted.get()));
    }

    // ── helpers ────────────────────────────────────────────────────

    private CardView toCardView(TaskRecord card) {
        List<String> allowed = boardService.get(card.boardId())
                .map(b -> snapshotService.allowedEventsFor(b, card))
                .orElse(List.of());
        return CardView.from(card, allowed);
    }
}
