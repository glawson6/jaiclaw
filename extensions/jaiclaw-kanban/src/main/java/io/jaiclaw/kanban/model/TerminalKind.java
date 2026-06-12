package io.jaiclaw.kanban.model;

/**
 * Classifies a terminal column's outcome so dashboards and metrics can
 * distinguish success/failure/cancelled completions.
 */
public enum TerminalKind {
    SUCCESS,
    FAILURE,
    CANCELLED
}
