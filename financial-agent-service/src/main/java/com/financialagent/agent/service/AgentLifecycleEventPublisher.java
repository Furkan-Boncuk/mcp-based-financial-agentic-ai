package com.financialagent.agent.service;

import com.financialagent.agent.config.AgentRabbitTopologyProperties;
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
}
