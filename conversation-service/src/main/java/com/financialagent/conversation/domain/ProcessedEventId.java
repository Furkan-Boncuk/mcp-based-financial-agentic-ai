package com.financialagent.conversation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProcessedEventId implements Serializable {

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "consumer_name", nullable = false, length = 128)
  private String consumerName;

  protected ProcessedEventId() {}

  public ProcessedEventId(UUID eventId, String consumerName) {
    this.eventId = eventId;
    this.consumerName = consumerName;
  }

  public UUID eventId() {
    return eventId;
  }

  public String consumerName() {
    return consumerName;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ProcessedEventId that)) {
      return false;
    }
    return Objects.equals(eventId, that.eventId) && Objects.equals(consumerName, that.consumerName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId, consumerName);
  }
}
