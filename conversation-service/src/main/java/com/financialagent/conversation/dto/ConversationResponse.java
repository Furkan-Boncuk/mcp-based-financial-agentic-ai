package com.financialagent.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id, String title, String description, UUID userId, Instant createdAt, Instant updatedAt) {}
