package com.financialagent.conversation.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID conversationId,
    UUID userId,
    String role,
    String content,
    Map<String, Object> metadata,
    Instant createdAt) {}
