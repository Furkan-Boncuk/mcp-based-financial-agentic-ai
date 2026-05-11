package com.financialagent.conversation.service;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.ServiceException;
import com.financialagent.conversation.common.web.CorrelationIdFilter;
import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.AgentTaskStatus;
import com.financialagent.conversation.domain.Conversation;
import com.financialagent.conversation.domain.Message;
import com.financialagent.conversation.domain.MessageIdempotencyKey;
import com.financialagent.conversation.domain.MessageRole;
import com.financialagent.conversation.domain.OutboxEvent;
import com.financialagent.conversation.dto.AgentTaskRefResponse;
import com.financialagent.conversation.dto.MessageCreateRequest;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.MessageIdempotencyKeyRepository;
import com.financialagent.conversation.repository.MessageRepository;
import com.financialagent.conversation.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageSubmitService {

  static final String RESEARCH_REQUESTED = "RESEARCH_REQUESTED";
  static final String AGENT_TASK_AGGREGATE = "AGENT_TASK";

  private final ConversationAccessService conversationAccessService;
  private final MessageRepository messageRepository;
  private final AgentTaskRepository agentTaskRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final MessageIdempotencyKeyRepository idempotencyKeyRepository;
  private final Duration idempotencyTtl;
  private final Clock clock;

  public MessageSubmitService(
      ConversationAccessService conversationAccessService,
      MessageRepository messageRepository,
      AgentTaskRepository agentTaskRepository,
      OutboxEventRepository outboxEventRepository,
      MessageIdempotencyKeyRepository idempotencyKeyRepository,
      @Value("${conversation.message-idempotency.ttl:7d}") Duration idempotencyTtl,
      Clock clock) {
    this.conversationAccessService = conversationAccessService;
    this.messageRepository = messageRepository;
    this.agentTaskRepository = agentTaskRepository;
    this.outboxEventRepository = outboxEventRepository;
    this.idempotencyKeyRepository = idempotencyKeyRepository;
    this.idempotencyTtl = idempotencyTtl;
    this.clock = clock;
  }

  @Transactional
  public AgentTaskRefResponse submitMessage(
      UUID userId, UUID conversationId, String idempotencyKey, MessageCreateRequest request) {
    String normalizedIdempotencyKey = requireIdempotencyKey(idempotencyKey);
    return idempotencyKeyRepository
        .findByIdUserIdAndIdIdempotencyKey(userId, normalizedIdempotencyKey)
        .map(this::toResponse)
        .orElseGet(
            () -> createSubmission(userId, conversationId, normalizedIdempotencyKey, request));
  }

  private AgentTaskRefResponse createSubmission(
      UUID userId, UUID conversationId, String idempotencyKey, MessageCreateRequest request) {
    Conversation conversation =
        conversationAccessService.requireOwnedConversation(userId, conversationId);
    Message message =
        messageRepository.save(
            new Message(
                conversation.id(),
                userId,
                MessageRole.USER,
                request.content().trim(),
                request.metadata()));
    AgentTask agentTask =
        agentTaskRepository.save(new AgentTask(conversation.id(), message.id(), userId));
    AgentTaskRefResponse response =
        new AgentTaskRefResponse(
            message.id(), agentTask.id(), agentTask.status().name(), agentTask.createdAt());
    UUID eventId = UUID.randomUUID();
    outboxEventRepository.save(
        new OutboxEvent(
            eventId,
            AGENT_TASK_AGGREGATE,
            agentTask.id(),
            RESEARCH_REQUESTED,
            researchRequestedPayload(
                eventId,
                userId,
                conversation.id(),
                message.id(),
                agentTask.id(),
                request.content().trim(),
                idempotencyKey),
            correlationId(),
            idempotencyKey));
    idempotencyKeyRepository.save(
        new MessageIdempotencyKey(
            userId,
            idempotencyKey,
            conversation.id(),
            message.id(),
            agentTask.id(),
            responsePayload(response),
            Instant.now(clock).plus(idempotencyTtl)));
    return response;
  }

  private String requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new ServiceException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key header is required");
    }
    String trimmed = idempotencyKey.trim();
    if (trimmed.length() > 128) {
      throw new ServiceException(
          ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be at most 128 characters");
    }
    return trimmed;
  }

  private AgentTaskRefResponse toResponse(MessageIdempotencyKey idempotencyKey) {
    Map<String, Object> payload = idempotencyKey.responsePayload();
    return new AgentTaskRefResponse(
        idempotencyKey.messageId(),
        idempotencyKey.agentTaskId(),
        (String) payload.getOrDefault("agentTaskStatus", AgentTaskStatus.PENDING.name()),
        Instant.parse((String) payload.get("createdAt")));
  }

  private Map<String, Object> responsePayload(AgentTaskRefResponse response) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("messageId", response.messageId().toString());
    payload.put("agentTaskId", response.agentTaskId().toString());
    payload.put("agentTaskStatus", response.agentTaskStatus());
    payload.put("createdAt", response.createdAt().toString());
    return payload;
  }

  private Map<String, Object> researchRequestedPayload(
      UUID eventId,
      UUID userId,
      UUID conversationId,
      UUID messageId,
      UUID agentTaskId,
      String userMessage,
      String idempotencyKey) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", eventId.toString());
    payload.put("schemaVersion", "1.0");
    payload.put("correlationId", correlationId());
    payload.put("idempotencyKey", idempotencyKey);
    payload.put("userId", userId.toString());
    payload.put("conversationId", conversationId.toString());
    payload.put("messageId", messageId.toString());
    payload.put("agentTaskId", agentTaskId.toString());
    payload.put("userMessage", userMessage);
    payload.put("occurredAt", Instant.now(clock).toString());
    payload.put("intentHint", "GENERAL_FINANCE");
    return payload;
  }

  private String correlationId() {
    return MDC.get(CorrelationIdFilter.MDC_KEY);
  }
}
