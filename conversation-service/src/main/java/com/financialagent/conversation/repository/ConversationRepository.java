package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  Page<Conversation> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

  Optional<Conversation> findByIdAndDeletedAtIsNull(UUID id);

  Optional<Conversation> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
}
