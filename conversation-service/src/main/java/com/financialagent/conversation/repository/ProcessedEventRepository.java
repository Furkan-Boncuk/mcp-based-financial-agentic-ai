package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.ProcessedEvent;
import com.financialagent.conversation.domain.ProcessedEventId;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

  boolean existsByIdEventId(UUID eventId);
}
