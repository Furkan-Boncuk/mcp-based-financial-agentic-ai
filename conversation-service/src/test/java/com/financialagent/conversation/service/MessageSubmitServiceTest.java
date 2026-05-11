package com.financialagent.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.ServiceException;
import com.financialagent.conversation.common.web.CorrelationIdFilter;
import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.Conversation;
import com.financialagent.conversation.domain.Message;
import com.financialagent.conversation.domain.MessageIdempotencyKey;
import com.financialagent.conversation.domain.MessageRole;
import com.financialagent.conversation.domain.OutboxEvent;
import com.financialagent.conversation.domain.OutboxEventStatus;
import com.financialagent.conversation.dto.MessageCreateRequest;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.ConversationRepository;
import com.financialagent.conversation.repository.MessageIdempotencyKeyRepository;
import com.financialagent.conversation.repository.MessageRepository;
import com.financialagent.conversation.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class MessageSubmitServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-10T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private ConversationRepository conversationRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private AgentTaskRepository agentTaskRepository;
  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private MessageIdempotencyKeyRepository idempotencyKeyRepository;

  private MessageSubmitService messageSubmitService;

  @BeforeEach
  void setUp() {
    ConversationAccessService conversationAccessService =
        new ConversationAccessService(conversationRepository);
    messageSubmitService =
        new MessageSubmitService(
            conversationAccessService,
            messageRepository,
            agentTaskRepository,
            outboxEventRepository,
            idempotencyKeyRepository,
            Duration.ofDays(7),
            CLOCK);
    MDC.put(CorrelationIdFilter.MDC_KEY, "correlation-id");
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void submitMessageCreatesMessageTaskOutboxAndIdempotencyRecord() {
    UUID userId = UUID.randomUUID();
    Conversation conversation = new Conversation(userId, "Chat", null);
    when(idempotencyKeyRepository.findByIdUserIdAndIdIdempotencyKey(userId, "idem-key"))
        .thenReturn(Optional.empty());
    when(conversationRepository.findByIdAndDeletedAtIsNull(conversation.id()))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.save(any(Message.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(agentTaskRepository.save(any(AgentTask.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response =
        messageSubmitService.submitMessage(
            userId,
            conversation.id(),
            " idem-key ",
            new MessageCreateRequest(" Ne alayım? ", Map.of("source", "test")));

    assertThat(response.messageId()).isNotNull();
    assertThat(response.agentTaskId()).isNotNull();
    assertThat(response.agentTaskStatus()).isEqualTo("PENDING");
    assertThat(response.createdAt()).isNotNull();

    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(messageRepository).save(messageCaptor.capture());
    assertThat(messageCaptor.getValue().content()).isEqualTo("Ne alayım?");
    assertThat(messageCaptor.getValue().role()).isEqualTo(MessageRole.USER);

    ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxEventRepository).save(outboxCaptor.capture());
    assertThat(outboxCaptor.getValue().eventType()).isEqualTo("RESEARCH_REQUESTED");
    assertThat(outboxCaptor.getValue().status()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(outboxCaptor.getValue().correlationId()).isEqualTo("correlation-id");
    assertThat(outboxCaptor.getValue().idempotencyKey()).isEqualTo("idem-key");
    assertThat(outboxCaptor.getValue().payload())
        .containsEntry("userId", userId.toString())
        .containsEntry("conversationId", conversation.id().toString())
        .containsEntry("userMessage", "Ne alayım?")
        .containsEntry("intentHint", "GENERAL_FINANCE");

    ArgumentCaptor<MessageIdempotencyKey> idempotencyCaptor =
        ArgumentCaptor.forClass(MessageIdempotencyKey.class);
    verify(idempotencyKeyRepository).save(idempotencyCaptor.capture());
    assertThat(idempotencyCaptor.getValue().messageId()).isEqualTo(response.messageId());
    assertThat(idempotencyCaptor.getValue().agentTaskId()).isEqualTo(response.agentTaskId());
  }

  @Test
  void duplicateIdempotencyKeyReturnsExistingResponseWithoutCreatingNewRows() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    UUID agentTaskId = UUID.randomUUID();
    MessageIdempotencyKey existing =
        new MessageIdempotencyKey(
            userId,
            "idem-key",
            conversationId,
            messageId,
            agentTaskId,
            Map.of("agentTaskStatus", "PENDING", "createdAt", NOW.toString()),
            NOW.plus(Duration.ofDays(7)));
    when(idempotencyKeyRepository.findByIdUserIdAndIdIdempotencyKey(userId, "idem-key"))
        .thenReturn(Optional.of(existing));

    var response =
        messageSubmitService.submitMessage(
            userId, conversationId, "idem-key", new MessageCreateRequest("Ne alayım?", Map.of()));

    assertThat(response.messageId()).isEqualTo(messageId);
    assertThat(response.agentTaskId()).isEqualTo(agentTaskId);
    verify(messageRepository, never()).save(any());
    verify(agentTaskRepository, never()).save(any());
    verify(outboxEventRepository, never()).save(any());
  }

  @Test
  void missingIdempotencyKeyIsRejected() {
    assertThatThrownBy(
            () ->
                messageSubmitService.submitMessage(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    " ",
                    new MessageCreateRequest("Ne alayım?", Map.of())))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.VALIDATION_FAILED);
  }

  @Test
  void submitMessageRejectsNonOwner() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    Conversation conversation = new Conversation(ownerId, "Chat", null);
    when(idempotencyKeyRepository.findByIdUserIdAndIdIdempotencyKey(requesterId, "idem-key"))
        .thenReturn(Optional.empty());
    when(conversationRepository.findByIdAndDeletedAtIsNull(conversation.id()))
        .thenReturn(Optional.of(conversation));

    assertThatThrownBy(
            () ->
                messageSubmitService.submitMessage(
                    requesterId,
                    conversation.id(),
                    "idem-key",
                    new MessageCreateRequest("Ne alayım?", Map.of())))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED);
  }
}
