package com.financialagent.conversation.service;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.ServiceException;
import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.Conversation;
import com.financialagent.conversation.dto.AgentTaskResponse;
import com.financialagent.conversation.dto.ConversationCreateRequest;
import com.financialagent.conversation.dto.ConversationResponse;
import com.financialagent.conversation.dto.MessageResponse;
import com.financialagent.conversation.mapper.ConversationMapper;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.ConversationRepository;
import com.financialagent.conversation.repository.MessageRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

  private final ConversationRepository conversationRepository;
  private final MessageRepository messageRepository;
  private final AgentTaskRepository agentTaskRepository;
  private final ConversationAccessService conversationAccessService;
  private final ConversationMapper mapper;

  public ConversationService(
      ConversationRepository conversationRepository,
      MessageRepository messageRepository,
      AgentTaskRepository agentTaskRepository,
      ConversationAccessService conversationAccessService,
      ConversationMapper mapper) {
    this.conversationRepository = conversationRepository;
    this.messageRepository = messageRepository;
    this.agentTaskRepository = agentTaskRepository;
    this.conversationAccessService = conversationAccessService;
    this.mapper = mapper;
  }

  @Transactional
  public ConversationResponse createConversation(UUID userId, ConversationCreateRequest request) {
    Conversation conversation =
        new Conversation(
            userId, request.title().trim(), normalizedDescription(request.description()));
    return mapper.toConversationResponse(conversationRepository.save(conversation));
  }

  @Transactional(readOnly = true)
  public Page<ConversationResponse> listConversations(UUID userId, Pageable pageable) {
    return conversationRepository
        .findByUserIdAndDeletedAtIsNull(userId, pageable)
        .map(mapper::toConversationResponse);
  }

  @Transactional(readOnly = true)
  public ConversationResponse getConversation(UUID userId, UUID conversationId) {
    return mapper.toConversationResponse(
        conversationAccessService.requireOwnedConversation(userId, conversationId));
  }

  @Transactional(readOnly = true)
  public Page<MessageResponse> listMessages(UUID userId, UUID conversationId, Pageable pageable) {
    conversationAccessService.requireOwnedConversation(userId, conversationId);
    return messageRepository
        .findByConversationIdOrderByCreatedAtAsc(conversationId, pageable)
        .map(mapper::toMessageResponse);
  }

  @Transactional
  public void deleteConversation(UUID userId, UUID conversationId) {
    Conversation conversation =
        conversationAccessService.requireOwnedConversation(userId, conversationId);
    conversation.softDelete();
    conversationRepository.save(conversation);
  }

  @Transactional(readOnly = true)
  public AgentTaskResponse getAgentTask(UUID userId, UUID agentTaskId) {
    AgentTask agentTask =
        agentTaskRepository
            .findById(agentTaskId)
            .orElseThrow(() -> new ServiceException(ErrorCode.AGENT_TASK_NOT_FOUND));
    if (!agentTask.userId().equals(userId)) {
      throw new ServiceException(ErrorCode.CONVERSATION_ACCESS_DENIED);
    }
    return mapper.toAgentTaskResponse(agentTask);
  }

  private String normalizedDescription(String description) {
    if (description == null || description.isBlank()) {
      return null;
    }
    return description.trim();
  }
}
