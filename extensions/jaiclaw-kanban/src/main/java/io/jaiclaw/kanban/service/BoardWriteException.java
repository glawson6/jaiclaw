package io.jaiclaw.kanban.service;

/**
 * Thrown when a write to {@link KanbanBoardService} is rejected because
 * {@code jaiclaw.kanban.boards.writable} is false. The REST layer maps
 * this onto a {@code 405 Method Not Allowed}.
 */
public class BoardWriteException extends RuntimeException {
    public BoardWriteException(String message) {
        super(message);
    }
}
