CREATE TABLE processed_agent_requests (
    event_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    agent_task_id UUID NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_processed_agent_requests_idempotency_key
    ON processed_agent_requests (idempotency_key);
