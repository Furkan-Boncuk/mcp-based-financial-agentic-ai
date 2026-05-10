package com.financialagent.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.ServiceException;
import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.Conversation;
import com.financialagent.conversation.domain.Message;
import com.financialagent.conversation.domain.MessageRole;
import com.financialagent.conversation.dto.ConversationCreateRequest;
import com.financialagent.conversation.mapper.ConversationMapper;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.ConversationRepository;
import com.financialagent.conversation.repository.MessageRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private AgentTaskRepository agentTaskRepository;

  private ConversationService conversationService;

  @BeforeEach
  void setUp() {
    conversationService =
        new ConversationService(
            conversationRepository,
            messageRepository,
            agentTaskRepository,
            new ConversationMapper());
  }

  @Test
  void createConversationNormalizesInputAndSavesForUser() {
    UUID userId = UUID.randomUUID();
    when(conversationRepository.save(any(Conversation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    conversationService.createConversation(
        userId, new ConversationCreateRequest(" Portfolio ", " Long term ideas "));

    ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(conversationCaptor.capture());
    assertThat(conversationCaptor.getValue().userId()).isEqualTo(userId);
    assertThat(conversationCaptor.getValue().title()).isEqualTo("Portfolio");
    assertThat(conversationCaptor.getValue().description()).isEqualTo("Long term ideas");
  }

  @Test
  void getConversationReturnsNotFoundWhenMissingOrDeleted() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findByIdAndDeletedAtIsNull(conversationId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.getConversation(userId, conversationId))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.CONVERSATION_NOT_FOUND);
  }

  @Test
  void getConversationRejectsNonOwner() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findByIdAndDeletedAtIsNull(conversationId))
        .thenReturn(Optional.of(new Conversation(ownerId, "Owner chat", null)));

    assertThatThrownBy(() -> conversationService.getConversation(requesterId, conversationId))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED);
  }

  @Test
  void listConversationsMapsOnlyRepositoryResultForUser() {
    UUID userId = UUID.randomUUID();
    PageRequest pageable = PageRequest.of(0, 20);
    when(conversationRepository.findByUserIdAndDeletedAtIsNull(userId, pageable))
        .thenReturn(new PageImpl<>(java.util.List.of(new Conversation(userId, "Chat", null))));

    assertThat(conversationService.listConversations(userId, pageable).getContent())
        .hasSize(1)
        .first()
        .extracting("title")
        .isEqualTo("Chat");
  }

  @Test
  void listMessagesRequiresConversationOwnership() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    PageRequest pageable = PageRequest.of(0, 50);
    when(conversationRepository.findByIdAndDeletedAtIsNull(conversationId))
        .thenReturn(Optional.of(new Conversation(userId, "Chat", null)));
    when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable))
        .thenReturn(
            new PageImpl<>(
                java.util.List.of(
                    new Message(conversationId, userId, MessageRole.USER, "Selam", Map.of()))));

    assertThat(conversationService.listMessages(userId, conversationId, pageable).getContent())
        .hasSize(1)
        .first()
        .extracting("content")
        .isEqualTo("Selam");
  }

  @Test
  void deleteConversationSoftDeletesOwnedConversation() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Conversation conversation = new Conversation(userId, "Chat", null);
    when(conversationRepository.findByIdAndDeletedAtIsNull(conversationId))
        .thenReturn(Optional.of(conversation));

    conversationService.deleteConversation(userId, conversationId);

    assertThat(conversation.deletedAt()).isNotNull();
    verify(conversationRepository).save(conversation);
  }

  @Test
  void getAgentTaskReturnsNotFoundWhenMissing() {
    UUID userId = UUID.randomUUID();
    UUID agentTaskId = UUID.randomUUID();
    when(agentTaskRepository.findById(agentTaskId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> conversationService.getAgentTask(userId, agentTaskId))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.AGENT_TASK_NOT_FOUND);
  }

  @Test
  void getAgentTaskRejectsNonOwner() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID agentTaskId = UUID.randomUUID();
    AgentTask agentTask = new AgentTask(UUID.randomUUID(), UUID.randomUUID(), ownerId);
    when(agentTaskRepository.findById(agentTaskId)).thenReturn(Optional.of(agentTask));

    assertThatThrownBy(() -> conversationService.getAgentTask(requesterId, agentTaskId))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED);
  }
}
