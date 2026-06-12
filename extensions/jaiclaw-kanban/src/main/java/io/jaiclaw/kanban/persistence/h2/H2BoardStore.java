package io.jaiclaw.kanban.persistence.h2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jaiclaw.kanban.model.BoardDefinition;
import io.jaiclaw.kanban.persistence.BoardStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * H2-backed {@link BoardStore}. The board definition is stored as a JSON
 * blob in the {@code definition_json} column — schema-free evolution: any
 * fields {@link BoardDefinition} grows in later phases are picked up by
 * Jackson without a DDL change.
 *
 * <p>Tenant scoping lives on {@link BoardDefinition#tenantIds()} (the
 * shared-with-all convention from Phase 1), so this table doesn't have a
 * tenant_id column. The {@code KanbanBoardService} applies the
 * visibility check on reads.
 */
public class H2BoardStore implements BoardStore {

    private static final Logger log = LoggerFactory.getLogger(H2BoardStore.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final RowMapper<BoardDefinition> rowMapper = new BoardRowMapper();

    public H2BoardStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(BoardDefinition board) {
        String payload;
        try {
            payload = json.writeValueAsString(board);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to encode board " + board.id() + " for H2 store", e);
        }
        // H2 supports `MERGE` with explicit KEY clause.
        jdbc.update("""
                MERGE INTO jaiclaw_kanban_boards (id, name, definition_json, updated_at)
                KEY (id)
                VALUES (?, ?, ?, ?)
                """,
                board.id(), board.name(), payload,
                Timestamp.from(Instant.now()));
    }

    @Override
    public boolean delete(String boardId) {
        if (boardId == null) return false;
        int rows = jdbc.update(
                "DELETE FROM jaiclaw_kanban_boards WHERE id = ?", boardId);
        return rows > 0;
    }

    @Override
    public Optional<BoardDefinition> findById(String boardId) {
        if (boardId == null) return Optional.empty();
        List<BoardDefinition> rows = jdbc.query(
                "SELECT * FROM jaiclaw_kanban_boards WHERE id = ?",
                rowMapper, boardId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<BoardDefinition> findAll() {
        return jdbc.query(
                "SELECT * FROM jaiclaw_kanban_boards ORDER BY id",
                rowMapper);
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM jaiclaw_kanban_boards", Long.class);
        return n == null ? 0L : n;
    }

    private class BoardRowMapper implements RowMapper<BoardDefinition> {
        @Override
        public BoardDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
            String payload = rs.getString("definition_json");
            if (payload == null || payload.isBlank()) return null;
            try {
                return json.readValue(payload, BoardDefinition.class);
            } catch (Exception e) {
                log.warn("Failed to decode board row {}: {}",
                        rs.getString("id"), e.getMessage());
                return null;
            }
        }
    }
}
