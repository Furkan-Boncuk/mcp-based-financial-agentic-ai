package com.financialagent.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

  @EmbeddedId private ProcessedEventId id;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private Instant processedAt;

  protected ProcessedEvent() {}

  public ProcessedEvent(UUID eventId, String consumerName) {
    this.id = new ProcessedEventId(eventId, consumerName);
  }

  @PrePersist
  void onCreate() {
    if (processedAt == null) {
      processedAt = Instant.now();
    }
  }

  public ProcessedEventId id() {
    return id;
  }

  public Instant processedAt() {
    return processedAt;
  }
}
