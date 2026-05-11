package com.financialagent.conversation.service;

import com.financialagent.conversation.config.AgentRabbitTopologyProperties;
import com.financialagent.conversation.domain.OutboxEvent;
import com.financialagent.conversation.domain.OutboxEventStatus;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "agent.rabbitmq.outbox", name = "enabled", havingValue = "true")
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxEventRepository outboxEventRepository;
  private final AgentTaskRepository agentTaskRepository;
  private final RabbitTemplate rabbitTemplate;
  private final AgentRabbitTopologyProperties properties;
  private final Clock clock;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      AgentTaskRepository agentTaskRepository,
      RabbitTemplate rabbitTemplate,
      AgentRabbitTopologyProperties properties,
      Clock clock) {
    this.outboxEventRepository = outboxEventRepository;
    this.agentTaskRepository = agentTaskRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${agent.rabbitmq.outbox.fixed-delay}")
  @Transactional
  public void publishPending() {
    Instant now = Instant.now(clock);
    logStalePendingEvents(now);
    List<OutboxEvent> events =
        outboxEventRepository.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING, now);
    events.forEach(this::publish);
  }

  private void publish(OutboxEvent event) {
    try {
      publishToRabbit(event);
      markPublished(event);
    } catch (RuntimeException exception) {
      event.markPublishFailed(
          exception.getMessage(),
          Instant.now(clock),
          properties.outbox().retryDelay(),
          properties.outbox().maxAttempts());
      outboxEventRepository.save(event);
    }
  }

  private void publishToRabbit(OutboxEvent event) {
    try {
      rabbitTemplate.invoke(
          operations -> {
            operations.convertAndSend(
                properties.exchanges().topic(),
                properties.routingKeys().researchRequested(),
                event.payload(),
                messagePostProcessor(event));
            operations.waitForConfirmsOrDie(properties.outbox().confirmTimeout().toMillis());
            return null;
          });
    } catch (Exception exception) {
      throw new AmqpException("Outbox event publish failed: " + event.id(), exception);
    }
  }

  private MessagePostProcessor messagePostProcessor(OutboxEvent event) {
    return message -> {
      message.getMessageProperties().setMessageId(event.id().toString());
      message.getMessageProperties().setCorrelationId(event.correlationId());
      message.getMessageProperties().setHeader("eventId", event.id().toString());
      message.getMessageProperties().setHeader("eventType", event.eventType());
      message.getMessageProperties().setHeader("idempotencyKey", event.idempotencyKey());
      return message;
    };
  }

  private void markPublished(OutboxEvent event) {
    event.markPublished(Instant.now(clock));
    outboxEventRepository.save(event);
    agentTaskRepository
        .findById(event.aggregateId())
        .ifPresent(
            agentTask -> {
              agentTask.markQueued();
              agentTaskRepository.save(agentTask);
            });
  }

  private void logStalePendingEvents(Instant now) {
    Instant staleBefore = now.minus(properties.outbox().staleThreshold());
    long staleCount =
        outboxEventRepository.countByStatusAndCreatedAtBefore(
            OutboxEventStatus.PENDING, staleBefore);
    if (staleCount > 0) {
      log.warn("Detected {} stale pending outbox event(s)", staleCount);
    }
  }
}
