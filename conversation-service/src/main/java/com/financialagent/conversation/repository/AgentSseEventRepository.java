package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.AgentSseEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSseEventRepository extends JpaRepository<AgentSseEvent, UUID> {

  List<AgentSseEvent> findTop10ByConversationIdOrderByOccurredAtDesc(UUID conversationId);

  Optional<AgentSseEvent> findByIdAndConversationId(UUID id, UUID conversationId);

  List<AgentSseEvent> findByConversationIdAndOccurredAtAfterOrderByOccurredAtAsc(
      UUID conversationId, Instant occurredAt);
}
