package com.financialagent.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_tasks")
public class AgentTask {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AgentTaskStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> resultMetadata;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected AgentTask() {}

  public AgentTask(UUID conversationId, UUID messageId, UUID userId) {
    this.id = UUID.randomUUID();
    this.conversationId = conversationId;
    this.messageId = messageId;
    this.userId = userId;
    this.status = AgentTaskStatus.PENDING;
    this.resultMetadata = Map.of();
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (status == null) {
      status = AgentTaskStatus.PENDING;
    }
    if (resultMetadata == null) {
      resultMetadata = Map.of();
    }
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID id() {
    return id;
  }

  public UUID conversationId() {
    return conversationId;
  }

  public UUID messageId() {
    return messageId;
  }

  public UUID userId() {
    return userId;
  }

  public AgentTaskStatus status() {
    return status;
  }

  public Map<String, Object> resultMetadata() {
    return resultMetadata;
  }

  public String errorCode() {
    return errorCode;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant completedAt() {
    return completedAt;
  }
}
