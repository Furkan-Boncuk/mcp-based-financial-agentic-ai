CREATE TABLE agent_progress_events (
    id UUID PRIMARY KEY,
    agent_task_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    stage VARCHAR(64),
    stage_detail TEXT,
    partial_content TEXT,
    tool_name VARCHAR(128),
    tool_status VARCHAR(32),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_progress_events_agent_task
        FOREIGN KEY (agent_task_id) REFERENCES agent_tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_progress_events_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_progress_events_message
        FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE
);

CREATE INDEX ix_agent_progress_events_task_occurred_at
    ON agent_progress_events (agent_task_id, occurred_at);
CREATE INDEX ix_agent_progress_events_conversation_occurred_at
    ON agent_progress_events (conversation_id, occurred_at);
