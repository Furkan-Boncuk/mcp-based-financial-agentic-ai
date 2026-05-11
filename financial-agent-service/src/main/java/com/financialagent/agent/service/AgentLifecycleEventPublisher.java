package com.financialagent.agent.service;

import com.financialagent.agent.config.AgentRabbitTopologyProperties;
import com.financialagent.agent.dto.AgentProgressUpdate;
import com.financialagent.agent.dto.AgentResearchResult;
import com.financialagent.agent.dto.ResearchRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentLifecycleEventPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final AgentRabbitTopologyProperties properties;
  private final Clock clock;

  public AgentLifecycleEventPublisher(
      RabbitTemplate rabbitTemplate, AgentRabbitTopologyProperties properties, Clock clock) {
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
    this.clock = clock;
  }

  public void publishStarted(ResearchRequestedEvent request) {
    Instant startedAt = Instant.now(clock);
    Map<String, Object> event = baseEvent(request);
    event.put("startedAt", startedAt.toString());
    event.put("intentDetected", intentDetected(request));
    event.put("toolsSelectedCount", 0);

    rabbitTemplate.convertAndSend(
        properties.exchanges().topic(), properties.routingKeys().researchStarted(), event);
  }

  public void publishProgressed(ResearchRequestedEvent request, AgentProgressUpdate progress) {
    Instant occurredAt = Instant.now(clock);
    Map<String, Object> event = baseEvent(request);
    event.put("stage", progress.stage());
    event.put("stageDetail", progress.stageDetail());
    event.put("partialContent", nullToEmpty(progress.partialContent()));
    event.put("toolName", progress.toolName());
    event.put("toolStatus", progress.toolStatus());
    event.put("occurredAt", occurredAt.toString());

    rabbitTemplate.convertAndSend(
        properties.exchanges().topic(), properties.routingKeys().researchProgressed(), event);
  }

  public void publishCompleted(ResearchRequestedEvent request, AgentResearchResult result) {
    Instant completedAt = Instant.now(clock);
    Map<String, Object> event = baseEvent(request);
    event.put("finalAnswer", result.finalAnswer());
    event.put("toolResults", result.toolResults());
    event.put("tokenUsage", result.tokenUsage());
    event.put("completedAt", completedAt.toString());
    event.put("source", result.source());

    rabbitTemplate.convertAndSend(
        properties.exchanges().topic(), properties.routingKeys().researchCompleted(), event);
  }

  public void publishFailed(ResearchRequestedEvent request, String errorCode, boolean retryable) {
    Instant failedAt = Instant.now(clock);
    Map<String, Object> event = baseEvent(request);
    event.put("errorCode", errorCode);
    event.put("errorMessage", "Agent processing failed");
    event.put("retryable", retryable);
    event.put("failedAt", failedAt.toString());
    event.put("attemptNumber", request.attemptNumber());

    rabbitTemplate.convertAndSend(
        properties.exchanges().topic(), properties.routingKeys().researchFailed(), event);
  }

  public void publishRetry(ResearchRequestedEvent request) {
    Map<String, Object> event = researchRequestedPayload(request, request.attemptNumber() + 1);
    rabbitTemplate.convertAndSend(
        properties.exchanges().retry(), retryRoutingKey(request.attemptNumber()), event);
  }

  public void publishDeadLettered(ResearchRequestedEvent request, String finalErrorCode) {
    Instant deadLetteredAt = Instant.now(clock);
    Map<String, Object> event = baseEvent(request);
    event.put("finalErrorCode", finalErrorCode);
    event.put("deadLetteredAt", deadLetteredAt.toString());

    rabbitTemplate.convertAndSend(
        properties.exchanges().topic(), properties.routingKeys().researchDeadlettered(), event);
  }

  private Map<String, Object> baseEvent(ResearchRequestedEvent request) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("eventId", UUID.randomUUID().toString());
    event.put("schemaVersion", "1.0");
    event.put("correlationId", request.correlationId());
    event.put("agentTaskId", request.agentTaskId().toString());
    event.put("userId", request.userId().toString());
    event.put("conversationId", request.conversationId().toString());
    event.put("messageId", request.messageId().toString());
    return event;
  }

  private String intentDetected(ResearchRequestedEvent request) {
    return request.intentHint() == null || request.intentHint().isBlank()
        ? "GENERAL_FINANCE"
        : request.intentHint();
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private Map<String, Object> researchRequestedPayload(
      ResearchRequestedEvent request, int attemptNumber) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("eventId", UUID.randomUUID().toString());
    event.put("schemaVersion", request.schemaVersion());
    event.put("correlationId", request.correlationId());
    event.put("idempotencyKey", request.idempotencyKey());
    event.put("userId", request.userId().toString());
    event.put("conversationId", request.conversationId().toString());
    event.put("messageId", request.messageId().toString());
    event.put("agentTaskId", request.agentTaskId().toString());
    event.put("userMessage", request.userMessage());
    event.put("occurredAt", Instant.now(clock).toString());
    event.put("intentHint", request.intentHint());
    event.put("attemptNumber", attemptNumber);
    return event;
  }

  private String retryRoutingKey(int attemptNumber) {
    if (attemptNumber <= 1) {
      return properties.routingKeys().retry1s();
    }
    if (attemptNumber == 2) {
      return properties.routingKeys().retry5s();
    }
    return properties.routingKeys().retry15s();
  }
}
