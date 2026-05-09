package com.financialagent.conversation.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AgentTaskResponse(
    UUID id,
    UUID conversationId,
    UUID messageId,
    UUID userId,
    String status,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    Map<String, Object> resultMetadata) {}
