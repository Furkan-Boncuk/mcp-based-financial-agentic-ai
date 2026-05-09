package com.financialagent.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record AgentTaskRefResponse(
    UUID messageId, UUID agentTaskId, String agentTaskStatus, Instant createdAt) {}
