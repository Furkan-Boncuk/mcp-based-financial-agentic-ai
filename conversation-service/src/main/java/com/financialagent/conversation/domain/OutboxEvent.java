package com.financialagent.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 64)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 128)
  private String eventType;

  @Column(name = "schema_version", nullable = false, length = 16)
  private String schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OutboxEventStatus status;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  protected OutboxEvent() {}

  public OutboxEvent(
      UUID id,
      String aggregateType,
      UUID aggregateId,
      String eventType,
      Map<String, Object> payload,
      String correlationId,
      String idempotencyKey) {
    this.id = id;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.payload = payload == null ? Map.of() : payload;
    this.correlationId = correlationId;
    this.idempotencyKey = idempotencyKey;
    this.schemaVersion = "1.0";
    this.status = OutboxEventStatus.PENDING;
  }

  public OutboxEvent(
      String aggregateType,
      UUID aggregateId,
      String eventType,
      Map<String, Object> payload,
      String correlationId,
      String idempotencyKey) {
    this(null, aggregateType, aggregateId, eventType, payload, correlationId, idempotencyKey);
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (schemaVersion == null || schemaVersion.isBlank()) {
      schemaVersion = "1.0";
    }
    if (payload == null) {
      payload = Map.of();
    }
    if (status == null) {
      status = OutboxEventStatus.PENDING;
    }
    if (occurredAt == null) {
      occurredAt = now;
    }
    createdAt = now;
    if (availableAt == null) {
      availableAt = now;
    }
  }

  public UUID id() {
    return id;
  }

  public String aggregateType() {
    return aggregateType;
  }

  public UUID aggregateId() {
    return aggregateId;
  }

  public String eventType() {
    return eventType;
  }

  public String schemaVersion() {
    return schemaVersion;
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public OutboxEventStatus status() {
    return status;
  }

  public String correlationId() {
    return correlationId;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant availableAt() {
    return availableAt;
  }

  public Instant publishedAt() {
    return publishedAt;
  }

  public int attempts() {
    return attempts;
  }

  public String lastError() {
    return lastError;
  }

  public void markPublished(Instant publishedAt) {
    this.status = OutboxEventStatus.PUBLISHED;
    this.publishedAt = publishedAt;
    this.lastError = null;
  }

  public void markPublishFailed(
      String errorMessage, Instant now, Duration retryDelay, int maxAttempts) {
    attempts++;
    lastError = sanitizedError(errorMessage);
    if (attempts >= maxAttempts) {
      status = OutboxEventStatus.FAILED;
      availableAt = now;
      return;
    }
    status = OutboxEventStatus.PENDING;
    availableAt = now.plus(retryDelay);
  }

  private String sanitizedError(String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return "Publish failed";
    }
    return errorMessage.length() > 4000 ? errorMessage.substring(0, 4000) : errorMessage;
  }
}
