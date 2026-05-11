package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.OutboxEvent;
import com.financialagent.conversation.domain.OutboxEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
      OutboxEventStatus status, Instant availableAt);

  long countByStatusAndCreatedAtBefore(OutboxEventStatus status, Instant createdBefore);
}
