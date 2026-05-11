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
@Table(name = "agent_progress_events")
public class AgentProgressEvent {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "agent_task_id", nullable = false)
  private UUID agentTaskId;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(length = 64)
  private String stage;

  @Column(name = "stage_detail", columnDefinition = "TEXT")
  private String stageDetail;

  @Column(name = "partial_content", columnDefinition = "TEXT")
  private String partialContent;

  @Column(name = "tool_name", length = 128)
  private String toolName;

  @Column(name = "tool_status", length = 32)
  private String toolStatus;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AgentProgressEvent() {}

  public AgentProgressEvent(
      UUID id,
      UUID agentTaskId,
      UUID conversationId,
      UUID messageId,
      UUID userId,
      String stage,
      String stageDetail,
      String partialContent,
      String toolName,
      String toolStatus,
      Map<String, Object> payload,
      Instant occurredAt) {
    this.id = id;
    this.agentTaskId = agentTaskId;
    this.conversationId = conversationId;
    this.messageId = messageId;
    this.userId = userId;
    this.stage = stage;
    this.stageDetail = stageDetail;
    this.partialContent = partialContent;
    this.toolName = toolName;
    this.toolStatus = toolStatus;
    this.payload = cleanPayload(payload);
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

  public UUID agentTaskId() {
    return agentTaskId;
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
