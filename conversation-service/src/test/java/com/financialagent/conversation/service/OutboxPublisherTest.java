package com.financialagent.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.conversation.config.AgentRabbitTopologyProperties;
import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.AgentTaskStatus;
import com.financialagent.conversation.domain.OutboxEvent;
import com.financialagent.conversation.domain.OutboxEventStatus;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  private static final Instant NOW = Instant.parse("2026-05-11T07:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private AgentTaskRepository agentTaskRepository;
  @Mock private RabbitTemplate rabbitTemplate;
  @Mock private RabbitOperations rabbitOperations;

  private OutboxPublisher outboxPublisher;

  @BeforeEach
  void setUp() {
    outboxPublisher =
        new OutboxPublisher(
            outboxEventRepository, agentTaskRepository, rabbitTemplate, properties(), CLOCK);
  }

  @Test
  void publishPendingPublishesEventMarksOutboxPublishedAndMovesTaskToQueued() {
    UUID agentTaskId = UUID.randomUUID();
    OutboxEvent event = researchRequestedEvent(agentTaskId);
    AgentTask agentTask = new AgentTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    when(outboxEventRepository.countByStatusAndCreatedAtBefore(
            eq(OutboxEventStatus.PENDING), any()))
        .thenReturn(0L);
    when(outboxEventRepository.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING, NOW))
        .thenReturn(List.of(event));
    when(agentTaskRepository.findById(agentTaskId)).thenReturn(Optional.of(agentTask));
    invokeRabbitCallbackSuccessfully();

    outboxPublisher.publishPending();

    assertThat(event.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(event.publishedAt()).isEqualTo(NOW);
    assertThat(agentTask.status()).isEqualTo(AgentTaskStatus.QUEUED);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitOperations)
        .convertAndSend(
            eq("agent.topic.exchange"),
            eq("agent.research.requested"),
            payloadCaptor.capture(),
            any(MessagePostProcessor.class));
    assertThat(payloadCaptor.getValue()).containsEntry("agentTaskId", agentTaskId.toString());
    verify(outboxEventRepository).save(event);
    verify(agentTaskRepository).save(agentTask);
  }

  @Test
  void publishFailureKeepsPendingEventAvailableForRetry() {
    OutboxEvent event = researchRequestedEvent(UUID.randomUUID());
    when(outboxEventRepository.countByStatusAndCreatedAtBefore(
            eq(OutboxEventStatus.PENDING), any()))
        .thenReturn(0L);
    when(outboxEventRepository.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING, NOW))
        .thenReturn(List.of(event));
    invokeRabbitCallbackWithFailure();

    outboxPublisher.publishPending();

    assertThat(event.status()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(event.attempts()).isEqualTo(1);
    assertThat(event.availableAt()).isEqualTo(NOW.plusSeconds(10));
    assertThat(event.lastError()).contains("Outbox event publish failed");
    verify(outboxEventRepository).save(event);
  }

  @Test
  void publishFailureMarksEventFailedAfterMaxAttempts() {
    OutboxEvent event = researchRequestedEvent(UUID.randomUUID());
    for (int i = 0; i < 9; i++) {
      event.markPublishFailed("previous failure", NOW, Duration.ZERO, 10);
    }
    when(outboxEventRepository.countByStatusAndCreatedAtBefore(
            eq(OutboxEventStatus.PENDING), any()))
        .thenReturn(0L);
    when(outboxEventRepository.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING, NOW))
        .thenReturn(List.of(event));
    invokeRabbitCallbackWithFailure();

    outboxPublisher.publishPending();

    assertThat(event.status()).isEqualTo(OutboxEventStatus.FAILED);
    assertThat(event.attempts()).isEqualTo(10);
    verify(outboxEventRepository).save(event);
  }

  private void invokeRabbitCallbackSuccessfully() {
    doAnswer(
            invocation -> {
              RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
              return callback.doInRabbit(rabbitOperations);
            })
        .when(rabbitTemplate)
        .invoke(any());
  }

  private void invokeRabbitCallbackWithFailure() {
    doAnswer(
            invocation -> {
              RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
              callback.doInRabbit(rabbitOperations);
              return null;
            })
        .when(rabbitTemplate)
        .invoke(any());
    doAnswer(
            invocation -> {
              throw new IllegalStateException("broker confirm failed");
            })
        .when(rabbitOperations)
        .waitForConfirmsOrDie(5_000);
  }

  private OutboxEvent researchRequestedEvent(UUID agentTaskId) {
    UUID eventId = UUID.randomUUID();
    return new OutboxEvent(
        eventId,
        "AGENT_TASK",
        agentTaskId,
        "RESEARCH_REQUESTED",
        Map.of("eventId", eventId.toString(), "agentTaskId", agentTaskId.toString()),
        "correlation-id",
        "idempotency-key");
  }

  private AgentRabbitTopologyProperties properties() {
    return new AgentRabbitTopologyProperties(
        new AgentRabbitTopologyProperties.Exchanges(
            "agent.topic.exchange", "agent.retry.exchange", "agent.dlq.exchange"),
        new AgentRabbitTopologyProperties.Queues(
            "agent.task.queue",
            "agent.result.queue",
            "agent.task.queue.retry.1s",
            "agent.task.queue.retry.5s",
            "agent.task.queue.retry.15s",
            "agent.dlq"),
        new AgentRabbitTopologyProperties.RoutingKeys(
            "agent.research.requested",
            "agent.research.started",
            "agent.research.progressed",
            "agent.research.completed",
            "agent.research.failed",
            "agent.research.deadlettered",
            "agent.research.*",
            "agent.research.requested.retry.1s",
            "agent.research.requested.retry.5s",
            "agent.research.requested.retry.15s"),
        Duration.ofHours(1),
        Duration.ofHours(1),
        Duration.ofDays(7),
        10_000,
        new AgentRabbitTopologyProperties.RetryDelays(
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(15)),
        new AgentRabbitTopologyProperties.Outbox(
            true,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            Duration.ofSeconds(10),
            10,
            Duration.ofMinutes(5)));
  }
}
