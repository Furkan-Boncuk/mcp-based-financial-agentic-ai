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
import java.util.LinkedHashMap;
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

  public void markQueued() {
    if (status == AgentTaskStatus.PENDING) {
      status = AgentTaskStatus.QUEUED;
    }
  }

  public boolean markRunning(Instant startedAt, Map<String, Object> metadata) {
    if (status != AgentTaskStatus.QUEUED) {
      return false;
    }
    status = AgentTaskStatus.RUNNING;
    this.startedAt = startedAt;
    mergeResultMetadata(metadata);
    return true;
  }

  public boolean markCompleted(Instant completedAt, Map<String, Object> metadata) {
    if (status != AgentTaskStatus.RUNNING) {
      return false;
    }
    status = AgentTaskStatus.COMPLETED;
    this.completedAt = completedAt;
    mergeResultMetadata(metadata);
    return true;
  }

  public boolean markFailed(String errorCode, String errorMessage, Instant failedAt) {
    if (status != AgentTaskStatus.RUNNING) {
      return false;
    }
    status = AgentTaskStatus.FAILED;
    this.errorCode = sanitized(errorCode, 64);
    this.errorMessage = sanitized(errorMessage, 4000);
    this.completedAt = failedAt;
    return true;
  }

  public boolean markDeadLettered(String errorCode, Instant deadLetteredAt) {
    if (status != AgentTaskStatus.FAILED) {
      return false;
    }
    status = AgentTaskStatus.DEAD_LETTERED;
    this.errorCode = sanitized(errorCode, 64);
    this.completedAt = deadLetteredAt;
    return true;
  }

  private void mergeResultMetadata(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return;
    }
    Map<String, Object> merged =
        new LinkedHashMap<>(resultMetadata == null ? Map.of() : resultMetadata);
    merged.putAll(metadata);
    resultMetadata = Map.copyOf(merged);
  }

  private String sanitized(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
  }
}
