package com.financialagent.agent.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ResearchRequestedEvent(
    UUID eventId,
    String schemaVersion,
    String correlationId,
    String idempotencyKey,
    UUID userId,
    UUID conversationId,
    UUID messageId,
    UUID agentTaskId,
    String userMessage,
    Instant occurredAt,
    String intentHint,
    int attemptNumber) {

  public static ResearchRequestedEvent from(Map<String, Object> payload) {
    return new ResearchRequestedEvent(
        uuid(payload, "eventId"),
        string(payload, "schemaVersion"),
        string(payload, "correlationId"),
        requiredString(payload, "idempotencyKey"),
        uuid(payload, "userId"),
        uuid(payload, "conversationId"),
        uuid(payload, "messageId"),
        uuid(payload, "agentTaskId"),
        requiredString(payload, "userMessage"),
        instant(payload, "occurredAt"),
        string(payload, "intentHint"),
        positiveInt(payload, "attemptNumber", 1));
  }

  private static UUID uuid(Map<String, Object> payload, String key) {
    return UUID.fromString(requiredString(payload, key));
  }

  private static Instant instant(Map<String, Object> payload, String key) {
    return Instant.parse(requiredString(payload, key));
  }

  private static String requiredString(Map<String, Object> payload, String key) {
    String value = string(payload, key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ResearchRequested event missing required field: " + key);
    }
    return value;
  }

  private static String string(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    return value == null ? null : value.toString();
  }

  private static int positiveInt(Map<String, Object> payload, String key, int defaultValue) {
    String value = string(payload, key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    int parsed = Integer.parseInt(value);
    return parsed <= 0 ? defaultValue : parsed;
  }
}
