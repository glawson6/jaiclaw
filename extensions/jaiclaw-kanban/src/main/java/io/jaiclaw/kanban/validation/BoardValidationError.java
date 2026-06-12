package io.jaiclaw.kanban.validation;

/**
 * One error surfaced by {@link BoardValidator}.
 *
 * @param boardId    board the error came from
 * @param location   field or sub-element the error references (e.g. {@code "transitions[2]"})
 * @param code       machine-readable error code
 * @param message    human-readable explanation
 * @param suggestion optional "did you mean?" hint
 */
public record BoardValidationError(
        String boardId,
        String location,
        String code,
        String message,
        String suggestion
) {
    public String formatted() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(code).append("] ").append(boardId)
                .append(" / ").append(location).append(": ").append(message);
        if (suggestion != null && !suggestion.isBlank()) {
            sb.append(" — did you mean '").append(suggestion).append("'?");
        }
        return sb.toString();
    }
}
