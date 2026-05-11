CREATE TABLE agent_sse_events (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    agent_task_id UUID NOT NULL,
    user_id UUID NOT NULL,
    event_name VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    terminal BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_sse_events_agent_task
        FOREIGN KEY (agent_task_id) REFERENCES agent_tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_sse_events_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE
);

CREATE INDEX ix_agent_sse_events_conversation_occurred_at
    ON agent_sse_events (conversation_id, occurred_at);
