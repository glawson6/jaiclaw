package io.jaiclaw.kanban.persistence;

import io.jaiclaw.kanban.model.BoardDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Durable storage for {@link BoardDefinition}s. Phase 2 ships
 * {@link YamlFileBoardStore} as the single implementation — YAML files in
 * {@code jaiclaw.kanban.boards-dir} are the source of truth, so ops can
 * {@code cat} / {@code git diff} / {@code git commit} board definitions
 * exactly like Pipeline YAML today.
 *
 * <p>Phase 4 may add an H2-backed store behind this SPI for multi-instance
 * deployments without changing the surface.
 *
 * <p>Writability is enforced at the {@code KanbanBoardService} layer via
 * {@code jaiclaw.kanban.boards.writable}; this SPI does not police writes.
 */
public interface BoardStore {

    /** Persist a board definition. Overwrites any existing entry with the same id. */
    void save(BoardDefinition board);

    /** Remove the board with the given id. Returns true when something was deleted. */
    boolean delete(String boardId);

    Optional<BoardDefinition> findById(String boardId);

    /** All persisted boards, in deterministic id order. */
    List<BoardDefinition> findAll();

    long count();
}
