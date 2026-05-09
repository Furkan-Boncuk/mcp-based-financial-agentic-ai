CREATE TABLE conversations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_conversations_user_created_at
    ON conversations (user_id, created_at DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX ix_conversations_deleted_at ON conversations (deleted_at);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT messages_role_check CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX ix_messages_conversation_created_at
    ON messages (conversation_id, created_at);
CREATE INDEX ix_messages_user_created_at ON messages (user_id, created_at DESC);

CREATE TABLE agent_tasks (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    result_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_code VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_agent_tasks_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tasks_message
        FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT agent_tasks_status_check
        CHECK (status IN ('PENDING', 'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'DEAD_LETTERED'))
);

CREATE INDEX ix_agent_tasks_user_status ON agent_tasks (user_id, status);
CREATE INDEX ix_agent_tasks_conversation_created_at
    ON agent_tasks (conversation_id, created_at DESC);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    schema_version VARCHAR(16) NOT NULL DEFAULT '1.0',
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    correlation_id VARCHAR(128),
    idempotency_key VARCHAR(128),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    CONSTRAINT outbox_events_status_check
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTERED'))
);

CREATE INDEX ix_outbox_events_status_created_at
    ON outbox_events (status, created_at);
CREATE INDEX ix_outbox_events_available_at
    ON outbox_events (available_at)
    WHERE status = 'PENDING';

CREATE TABLE processed_events (
    event_id UUID NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer_name)
);

CREATE UNIQUE INDEX ux_processed_events_event_id ON processed_events (event_id);

CREATE TABLE message_idempotency_keys (
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    conversation_id UUID NOT NULL,
    message_id UUID NOT NULL,
    agent_task_id UUID NOT NULL,
    response_payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, idempotency_key),
    CONSTRAINT fk_message_idempotency_keys_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_idempotency_keys_message
        FOREIGN KEY (message_id) REFERENCES messages (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_idempotency_keys_agent_task
        FOREIGN KEY (agent_task_id) REFERENCES agent_tasks (id) ON DELETE CASCADE
);

CREATE INDEX ix_message_idempotency_keys_expires_at
    ON message_idempotency_keys (expires_at);
