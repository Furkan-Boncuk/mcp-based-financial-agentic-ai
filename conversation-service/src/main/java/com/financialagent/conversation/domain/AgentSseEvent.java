package com.financialagent.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_sse_events")
public class AgentSseEvent {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "agent_task_id", nullable = false)
  private UUID agentTaskId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "event_name", nullable = false, length = 64)
  private String eventName;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Column(nullable = false)
  private boolean terminal;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AgentSseEvent() {}

  public AgentSseEvent(
      UUID id,
      UUID conversationId,
      UUID agentTaskId,
      UUID userId,
      String eventName,
      Map<String, Object> payload,
      boolean terminal,
      Instant occurredAt) {
    this.id = id;
    this.conversationId = conversationId;
    this.agentTaskId = agentTaskId;
    this.userId = userId;
    this.eventName = eventName;
    this.payload = cleanPayload(payload);
    this.terminal = terminal;
    this.occurredAt = occurredAt;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  public UUID id() {
    return id;
  }

  public UUID conversationId() {
    return conversationId;
  }

  public String eventName() {
    return eventName;
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public boolean terminal() {
    return terminal;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  private Map<String, Object> cleanPayload(Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> cleanPayload = new LinkedHashMap<>();
    payload.forEach(
        (key, value) -> {
          if (value != null) {
            cleanPayload.put(key, value);
          }
        });
    return Map.copyOf(cleanPayload);
  }
}
