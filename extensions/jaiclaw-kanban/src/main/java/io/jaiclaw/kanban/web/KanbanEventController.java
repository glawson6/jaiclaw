package io.jaiclaw.kanban.web;

import io.jaiclaw.kanban.service.KanbanBoardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events endpoint for the kanban dashboard.
 *
 * <p>{@code GET /api/kanban/boards/{boardId}/events} returns a single
 * {@link SseEmitter} that streams two kinds of events:
 * <ul>
 *   <li>{@code snapshot} — sent once immediately on connect, payload is the
 *       full {@link io.jaiclaw.kanban.model.BoardSnapshot}.</li>
 *   <li>{@code state-changed} — sent per accepted card transition for this
 *       board, payload is the {@link io.jaiclaw.kanban.events.TaskStateChanged}
 *       event from the engine.</li>
 * </ul>
 *
 * <p>The per-tenant max-connections cap is enforced by
 * {@link KanbanEventBroadcaster#register}; the controller maps that onto
 * {@code 429 Too Many Requests}. Unknown boards (or boards not visible to
 * the current tenant) return {@code 404 Not Found}.
 */
@RestController
@RequestMapping("${jaiclaw.kanban.http.base-path:/api/kanban}")
public class KanbanEventController {

    /**
     * Long timeout — Spring's default would close the emitter after 30s,
     * which kills any idle dashboard. The heartbeat keeps proxies happy;
     * the client should reconnect on browser-level errors via
     * EventSource's built-in auto-retry.
     */
    private static final long EMITTER_TIMEOUT_MS = 60L * 60L * 1000L;

    private final KanbanEventBroadcaster broadcaster;
    private final KanbanBoardService boardService;

    public KanbanEventController(KanbanEventBroadcaster broadcaster,
                                 KanbanBoardService boardService) {
        this.broadcaster = broadcaster;
        this.boardService = boardService;
    }

    @GetMapping("/boards/{boardId}/events")
    public ResponseEntity<?> stream(@PathVariable("boardId") String boardId) {
        if (boardService.get(boardId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Dtos.ErrorBody.of("board not found: " + boardId));
        }
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        SseEmitter registered = broadcaster.register(boardId, emitter);
        if (registered == null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Dtos.ErrorBody.of(
                            "max SSE connections reached for tenant",
                            "lower the dashboard polling rate or raise jaiclaw.kanban.sse.max-connections"));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(registered);
    }
}
