package com.financialagent.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MessageIdempotencyKeyId implements Serializable {

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  protected MessageIdempotencyKeyId() {}

  public MessageIdempotencyKeyId(UUID userId, String idempotencyKey) {
    this.userId = userId;
    this.idempotencyKey = idempotencyKey;
  }

  public UUID userId() {
    return userId;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof MessageIdempotencyKeyId that)) {
      return false;
    }
    return Objects.equals(userId, that.userId)
        && Objects.equals(idempotencyKey, that.idempotencyKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, idempotencyKey);
  }
}
