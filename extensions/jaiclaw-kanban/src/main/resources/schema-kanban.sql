-- Phase 4 H2-backed BoardStore (plan §9).
-- Activated by jaiclaw.kanban.boards.type=h2.

CREATE TABLE IF NOT EXISTS jaiclaw_kanban_boards (
    id              VARCHAR(255) NOT NULL PRIMARY KEY,
    name            VARCHAR(512),
    definition_json CLOB NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);
