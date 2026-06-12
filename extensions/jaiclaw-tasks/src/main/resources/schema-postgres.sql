-- Phase 4b Postgres schema variant — same shape as schema.sql, with
-- CLOB → TEXT (the only H2/Postgres divergence the H2TaskStore actually
-- hits). Apps using Postgres should set
--   spring.sql.init.schema-locations=classpath:schema-postgres.sql

CREATE TABLE IF NOT EXISTS jaiclaw_tasks (
    -- Composite primary key: two tenants may legitimately reuse the same
    -- task id without colliding. The JsonFileTaskStore makes the same
    -- guarantee via a "{tenantId}:{taskId}" key prefix.
    id              VARCHAR(255) NOT NULL,
    tenant_id       VARCHAR(255) NOT NULL DEFAULT 'default',
    name            VARCHAR(512),
    description     TEXT,
    status          VARCHAR(32) NOT NULL,
    delivery_state  VARCHAR(32) NOT NULL,
    result          TEXT,
    error           TEXT,
    flow_id         VARCHAR(255),
    metadata_json   TEXT,
    created_at      TIMESTAMP NOT NULL,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,

    -- Phase 1 kanban-extension fields.
    board_id        VARCHAR(255),
    state           VARCHAR(255),
    assignee        VARCHAR(255),
    version         BIGINT NOT NULL DEFAULT 0,
    order_index     INT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(512),

    -- Phase 4 lease columns for multi-instance recovery (plan §9 / analysis §6.5).
    claimed_by      VARCHAR(255),
    lease_until     TIMESTAMP,

    PRIMARY KEY (id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_tasks_tenant         ON jaiclaw_tasks (tenant_id);
CREATE INDEX IF NOT EXISTS idx_tasks_status         ON jaiclaw_tasks (status);
CREATE INDEX IF NOT EXISTS idx_tasks_board_state    ON jaiclaw_tasks (board_id, state);
CREATE INDEX IF NOT EXISTS idx_tasks_lease          ON jaiclaw_tasks (lease_until);
