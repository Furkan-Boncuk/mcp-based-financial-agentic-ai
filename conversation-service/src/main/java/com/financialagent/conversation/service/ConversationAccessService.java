package com.financialagent.conversation.service;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.ServiceException;
import com.financialagent.conversation.domain.Conversation;
import com.financialagent.conversation.repository.ConversationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConversationAccessService {

  private final ConversationRepository conversationRepository;

  public ConversationAccessService(ConversationRepository conversationRepository) {
    this.conversationRepository = conversationRepository;
  }

  public Conversation requireOwnedConversation(UUID userId, UUID conversationId) {
    Conversation conversation =
        conversationRepository
            .findByIdAndDeletedAtIsNull(conversationId)
            .orElseThrow(() -> new ServiceException(ErrorCode.CONVERSATION_NOT_FOUND));
    if (!conversation.userId().equals(userId)) {
      throw new ServiceException(ErrorCode.CONVERSATION_ACCESS_DENIED);
    }
    return conversation;
  }
}
