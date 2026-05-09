package com.financialagent.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "message_idempotency_keys")
public class MessageIdempotencyKey {

  @EmbeddedId private MessageIdempotencyKeyId id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Column(name = "agent_task_id", nullable = false)
  private UUID agentTaskId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_payload", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> responsePayload;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  protected MessageIdempotencyKey() {}

  public MessageIdempotencyKey(
      UUID userId,
      String idempotencyKey,
      UUID conversationId,
      UUID messageId,
      UUID agentTaskId,
      Map<String, Object> responsePayload,
      Instant expiresAt) {
    this.id = new MessageIdempotencyKeyId(userId, idempotencyKey);
    this.conversationId = conversationId;
    this.messageId = messageId;
    this.agentTaskId = agentTaskId;
    this.responsePayload = responsePayload == null ? Map.of() : responsePayload;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (responsePayload == null) {
      responsePayload = Map.of();
    }
  }

  public MessageIdempotencyKeyId id() {
    return id;
  }

  public UUID conversationId() {
    return conversationId;
  }

  public UUID messageId() {
    return messageId;
  }

  public UUID agentTaskId() {
    return agentTaskId;
  }

  public Map<String, Object> responsePayload() {
    return responsePayload;
  }
}
