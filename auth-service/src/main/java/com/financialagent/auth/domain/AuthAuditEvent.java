package com.financialagent.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_audit_events")
public class AuthAuditEvent {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  @Column(nullable = false)
  private boolean success;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(columnDefinition = "TEXT")
  private String metadata;

  protected AuthAuditEvent() {}

  public AuthAuditEvent(
      UUID userId,
      String action,
      String ipAddress,
      String userAgent,
      String correlationId,
      boolean success,
      String errorCode,
      String metadata) {
    this.userId = userId;
    this.action = action;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.correlationId = correlationId;
    this.success = success;
    this.errorCode = errorCode;
    this.metadata = metadata;
  }

  @PrePersist
  void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (occurredAt == null) {
      occurredAt = Instant.now();
    }
  }

  public UUID id() {
    return id;
  }

  public UUID userId() {
    return userId;
  }

  public String action() {
    return action;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public boolean success() {
    return success;
  }

  public String errorCode() {
    return errorCode;
  }
}
