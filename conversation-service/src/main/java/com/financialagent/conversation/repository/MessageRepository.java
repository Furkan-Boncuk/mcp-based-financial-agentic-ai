package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.Message;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  Page<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);

  @Query(
      value =
          """
          SELECT EXISTS (
              SELECT 1
              FROM messages
              WHERE role = 'ASSISTANT'
                AND metadata ->> 'agentTaskId' = CAST(:agentTaskId AS text)
          )
          """,
      nativeQuery = true)
  boolean existsAssistantMessageForAgentTask(@Param("agentTaskId") UUID agentTaskId);
}
