package com.financialagent.conversation.repository;

import com.financialagent.conversation.domain.MessageIdempotencyKey;
import com.financialagent.conversation.domain.MessageIdempotencyKeyId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageIdempotencyKeyRepository
    extends JpaRepository<MessageIdempotencyKey, MessageIdempotencyKeyId> {

  Optional<MessageIdempotencyKey> findByIdUserIdAndIdIdempotencyKey(
      UUID userId, String idempotencyKey);
}
