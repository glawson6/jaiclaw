package io.jaiclaw.kanban.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.jaiclaw.kanban.model.BoardDefinition;

import java.util.Map;

/**
 * Container for the small DTO records the REST controller accepts and
 * returns. Inlined here rather than spread across one-record-per-file —
 * each one is tiny, none have logic, and grouping them keeps the web
 * package skimmable.
 */
public final class Dtos {

    private Dtos() {}

    /** Body of {@code POST /api/kanban/boards/{boardId}/tasks}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateCardRequest(String name, String description, Map<String, String> metadata) {
        public CreateCardRequest {
            if (metadata == null) metadata = Map.of();
        }
    }

    /** Body of {@code POST /api/kanban/tasks/{taskId}/transition}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransitionRequest(String event, String actor, String comment) {}

    /** Body of {@code POST /api/kanban/tasks/{taskId}/claim}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClaimRequest(String assignee) {}

    /** Short summary returned by {@code GET /api/kanban/boards}. */
    public record BoardSummary(
            String id,
            String name,
            String initialState,
            int columnCount,
            int transitionCount) {
        public static BoardSummary of(BoardDefinition def) {
            return new BoardSummary(
                    def.id(), def.name(), def.initialState(),
                    def.columns().size(), def.transitions().size());
        }
    }

    /** Standard error response body. */
    public record ErrorBody(String error, String reason) {
        public static ErrorBody of(String message) {
            return new ErrorBody(message, null);
        }
        public static ErrorBody of(String message, String reason) {
            return new ErrorBody(message, reason);
        }
    }
}
