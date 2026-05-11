package com.financialagent.conversation.service;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.ServiceException;
import com.financialagent.conversation.domain.AgentProgressEvent;
import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.Message;
import com.financialagent.conversation.domain.MessageRole;
import com.financialagent.conversation.domain.ProcessedEvent;
import com.financialagent.conversation.repository.AgentProgressEventRepository;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.MessageRepository;
import com.financialagent.conversation.repository.ProcessedEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentResultEventService {

  private static final Logger log = LoggerFactory.getLogger(AgentResultEventService.class);
  private static final String CONSUMER_NAME = "conversation-service.agent-result-consumer";

  private final AgentTaskRepository agentTaskRepository;
  private final MessageRepository messageRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final AgentProgressEventRepository agentProgressEventRepository;
  private final Clock clock;

  public AgentResultEventService(
      AgentTaskRepository agentTaskRepository,
      MessageRepository messageRepository,
      ProcessedEventRepository processedEventRepository,
      AgentProgressEventRepository agentProgressEventRepository,
      Clock clock) {
    this.agentTaskRepository = agentTaskRepository;
    this.messageRepository = messageRepository;
    this.processedEventRepository = processedEventRepository;
    this.agentProgressEventRepository = agentProgressEventRepository;
    this.clock = clock;
  }

  @Transactional
  public void handle(String routingKey, Map<String, Object> payload) {
    AgentResultEventType eventType = AgentResultEventType.fromRoutingKey(routingKey);
    if (eventType == AgentResultEventType.IGNORED) {
      log.debug("Ignoring non-result agent event with routing key {}", routingKey);
      return;
    }

    UUID eventId = uuid(payload, "eventId");
    if (processedEventRepository.existsByIdEventId(eventId)) {
      log.debug("Skipping duplicate agent result event {}", eventId);
      return;
    }

    AgentTask agentTask =
        agentTaskRepository
            .findById(uuid(payload, "agentTaskId"))
            .orElseThrow(() -> new ServiceException(ErrorCode.AGENT_TASK_NOT_FOUND));

    switch (eventType) {
      case STARTED -> handleStarted(agentTask, payload);
      case PROGRESSED -> handleProgressed(payload);
      case COMPLETED -> handleCompleted(agentTask, payload);
      case FAILED -> handleFailed(agentTask, payload);
      case DEAD_LETTERED -> handleDeadLettered(agentTask, payload);
      case IGNORED -> {
        return;
      }
    }

    processedEventRepository.save(new ProcessedEvent(eventId, CONSUMER_NAME));
  }

  private void handleStarted(AgentTask agentTask, Map<String, Object> payload) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfPresent(metadata, "intentDetected", string(payload, "intentDetected"));
    putIfPresent(metadata, "toolsSelectedCount", payload.get("toolsSelectedCount"));
    boolean changed =
        agentTask.markRunning(instant(payload, "startedAt", Instant.now(clock)), metadata);
    saveOrLogInvalid(agentTask, "AgentStarted", changed);
  }

  private void handleProgressed(Map<String, Object> payload) {
    agentProgressEventRepository.save(
        new AgentProgressEvent(
            uuid(payload, "eventId"),
            uuid(payload, "agentTaskId"),
            uuid(payload, "conversationId"),
            uuid(payload, "messageId"),
            uuid(payload, "userId"),
            string(payload, "stage"),
            string(payload, "stageDetail"),
            string(payload, "partialContent"),
            string(payload, "toolName"),
            string(payload, "toolStatus"),
            payload,
            instant(payload, "occurredAt", Instant.now(clock))));
  }

  private void handleCompleted(AgentTask agentTask, Map<String, Object> payload) {
    Map<String, Object> metadata = completionMetadata(payload);
    boolean changed =
        agentTask.markCompleted(instant(payload, "completedAt", Instant.now(clock)), metadata);
    saveOrLogInvalid(agentTask, "AgentCompleted", changed);
    if (changed && !messageRepository.existsAssistantMessageForAgentTask(agentTask.id())) {
      messageRepository.save(
          new Message(
              agentTask.conversationId(),
              agentTask.userId(),
              MessageRole.ASSISTANT,
              requiredString(payload, "finalAnswer"),
              assistantMessageMetadata(agentTask.id(), metadata)));
    }
  }

  private void handleFailed(AgentTask agentTask, Map<String, Object> payload) {
    boolean changed =
        agentTask.markFailed(
            string(payload, "errorCode"),
            string(payload, "errorMessage"),
            instant(payload, "failedAt", Instant.now(clock)));
    saveOrLogInvalid(agentTask, "AgentFailed", changed);
  }

  private void handleDeadLettered(AgentTask agentTask, Map<String, Object> payload) {
    boolean changed =
        agentTask.markDeadLettered(
            string(payload, "finalErrorCode"),
            instant(payload, "deadLetteredAt", Instant.now(clock)));
    saveOrLogInvalid(agentTask, "AgentDeadLettered", changed);
  }

  private void saveOrLogInvalid(AgentTask agentTask, String eventName, boolean changed) {
    if (!changed) {
      log.warn(
          "Rejected invalid {} state transition for agentTaskId={} currentStatus={}",
          eventName,
          agentTask.id(),
          agentTask.status());
      return;
    }
    agentTaskRepository.save(agentTask);
  }

  private Map<String, Object> completionMetadata(Map<String, Object> payload) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    putIfPresent(metadata, "toolResults", payload.get("toolResults"));
    putIfPresent(metadata, "tokenUsage", payload.get("tokenUsage"));
    putIfPresent(metadata, "source", payload.get("source"));
    return metadata;
  }

  private Map<String, Object> assistantMessageMetadata(
      UUID agentTaskId, Map<String, Object> completionMetadata) {
    Map<String, Object> metadata = new LinkedHashMap<>(completionMetadata);
    metadata.put("agentTaskId", agentTaskId.toString());
    return metadata;
  }

  private void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (value != null) {
      target.put(key, value);
    }
  }

  private UUID uuid(Map<String, Object> payload, String key) {
    return UUID.fromString(requiredString(payload, key));
  }

  private String requiredString(Map<String, Object> payload, String key) {
    String value = string(payload, key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Agent result event missing required field: " + key);
    }
    return value;
  }

  private String string(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    return value == null ? null : value.toString();
  }

  private Instant instant(Map<String, Object> payload, String key, Instant defaultValue) {
    String value = string(payload, key);
    return value == null || value.isBlank() ? defaultValue : Instant.parse(value);
  }

  private enum AgentResultEventType {
    STARTED,
    PROGRESSED,
    COMPLETED,
    FAILED,
    DEAD_LETTERED,
    IGNORED;

    static AgentResultEventType fromRoutingKey(String routingKey) {
      return switch (routingKey) {
        case "agent.research.started" -> STARTED;
        case "agent.research.progressed" -> PROGRESSED;
        case "agent.research.completed" -> COMPLETED;
        case "agent.research.failed" -> FAILED;
        case "agent.research.deadlettered" -> DEAD_LETTERED;
        default -> IGNORED;
      };
    }
  }
}
