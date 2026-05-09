CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    tier VARCHAR(32) NOT NULL DEFAULT 'free',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT users_tier_check CHECK (tier IN ('free', 'premium'))
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));
CREATE INDEX ix_users_deleted_at ON users (deleted_at);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_roles_name UNIQUE (name)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE TABLE auth_audit_events (
    id UUID PRIMARY KEY,
    user_id UUID,
    action VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    correlation_id VARCHAR(128),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_code VARCHAR(64),
    metadata TEXT,
    CONSTRAINT fk_auth_audit_events_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_auth_audit_events_user_id ON auth_audit_events (user_id);
CREATE INDEX ix_auth_audit_events_action ON auth_audit_events (action);
CREATE INDEX ix_auth_audit_events_occurred_at ON auth_audit_events (occurred_at);
