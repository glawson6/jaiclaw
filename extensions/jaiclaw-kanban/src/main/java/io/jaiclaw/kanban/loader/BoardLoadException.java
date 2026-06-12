package io.jaiclaw.kanban.loader;

/** Thrown when a board YAML file cannot be parsed. */
public class BoardLoadException extends RuntimeException {
    public BoardLoadException(String message) {
        super(message);
    }

    public BoardLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
