package com.financialagent.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "messages")
public class Message {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MessageRole role;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Message() {}

  public Message(
      UUID conversationId,
      UUID userId,
      MessageRole role,
      String content,
      Map<String, Object> metadata) {
    this.conversationId = conversationId;
    this.userId = userId;
    this.role = role;
    this.content = content;
    this.metadata = metadata == null ? Map.of() : metadata;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (metadata == null) {
      metadata = Map.of();
    }
  }

  public UUID id() {
    return id;
  }

  public UUID conversationId() {
    return conversationId;
  }

  public UUID userId() {
    return userId;
  }

  public MessageRole role() {
    return role;
  }

  public String content() {
    return content;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
