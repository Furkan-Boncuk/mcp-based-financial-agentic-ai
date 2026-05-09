package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.Message;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  Page<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);
}
