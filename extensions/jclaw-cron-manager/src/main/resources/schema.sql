CREATE TABLE IF NOT EXISTS cron_job_definitions (
    id              VARCHAR(255) NOT NULL PRIMARY KEY,
    name            VARCHAR(255),
    agent_id        VARCHAR(255) NOT NULL DEFAULT 'default',
    schedule        VARCHAR(255) NOT NULL,
    timezone        VARCHAR(64) NOT NULL DEFAULT 'UTC',
    prompt          CLOB,
    delivery_channel VARCHAR(255),
    delivery_target VARCHAR(255),
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMP,
    next_run_at     TIMESTAMP,
    provider        VARCHAR(255),
    model           VARCHAR(255),
    system_prompt   CLOB,
    tool_profile    VARCHAR(64) NOT NULL DEFAULT 'MINIMAL',
    skills          VARCHAR(2048) DEFAULT ''
);

CREATE TABLE IF NOT EXISTS cron_execution_history (
    run_id          VARCHAR(255) NOT NULL PRIMARY KEY,
    job_id          VARCHAR(255) NOT NULL,
    job_name        VARCHAR(255),
    status          VARCHAR(32) NOT NULL,
    result          CLOB,
    started_at      TIMESTAMP NOT NULL,
    completed_at    TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exec_job_id ON cron_execution_history (job_id);
CREATE INDEX IF NOT EXISTS idx_exec_status ON cron_execution_history (status);
