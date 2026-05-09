package com.financialagent.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(length = 255)
  private String name;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(nullable = false, length = 32)
  private String tier;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  protected User() {}

  public User(UUID id, String email, String name, String passwordHash, String tier) {
    this.id = id;
    this.email = email;
    this.name = name;
    this.passwordHash = passwordHash;
    this.tier = tier;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (id == null) {
      id = UUID.randomUUID();
    }
    if (tier == null || tier.isBlank()) {
      tier = "free";
    }
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID id() {
    return id;
  }

  public String email() {
    return email;
  }

  public String name() {
    return name;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public String tier() {
    return tier;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Instant deletedAt() {
    return deletedAt;
  }
}
