package com.financialagent.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.conversation.config.AgentRabbitTopologyConfig;
import com.financialagent.conversation.config.AgentRabbitTopologyProperties;
import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.AgentTaskStatus;
import com.financialagent.conversation.domain.OutboxEvent;
import com.financialagent.conversation.domain.OutboxEventStatus;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.OutboxEventRepository;
import com.rabbitmq.client.GetResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class OutboxPublisherRabbitMqIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-05-11T09:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Container
  private static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

  private CachingConnectionFactory connectionFactory;

  @BeforeEach
  void setUp() {
    connectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
    connectionFactory.setUsername(RABBITMQ.getAdminUsername());
    connectionFactory.setPassword(RABBITMQ.getAdminPassword());
    connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);

    RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
    AgentRabbitTopologyConfig config = new AgentRabbitTopologyConfig(properties());
    config
        .agentRabbitDeclarables()
        .getDeclarables()
        .forEach(declarable -> declare(rabbitAdmin, declarable));
    rabbitAdmin.purgeQueue(properties().queues().task(), false);
    rabbitAdmin.purgeQueue(properties().queues().dlq(), false);
  }

  @AfterEach
  void tearDown() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void publishesPendingOutboxEventToRabbitMqAndMarksTaskQueued() {
    UUID eventId = UUID.randomUUID();
    UUID agentTaskId = UUID.randomUUID();
    Map<String, Object> payload =
        Map.of(
            "eventId", eventId.toString(),
            "schemaVersion", "1.0",
            "correlationId", "correlation-id",
            "idempotencyKey", "idempotency-key",
            "agentTaskId", agentTaskId.toString(),
            "userMessage", "Ne alayım?",
            "occurredAt", NOW.toString());
    OutboxEvent event =
        new OutboxEvent(
            eventId,
            "AGENT_TASK",
            agentTaskId,
            "RESEARCH_REQUESTED",
            payload,
            "correlation-id",
            "idempotency-key");
    AgentTask agentTask = new AgentTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    AgentTaskRepository agentTaskRepository = mock(AgentTaskRepository.class);
    when(outboxEventRepository.countByStatusAndCreatedAtBefore(
            eq(OutboxEventStatus.PENDING), any()))
        .thenReturn(0L);
    when(outboxEventRepository.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING, NOW))
        .thenReturn(List.of(event));
    when(agentTaskRepository.findById(agentTaskId)).thenReturn(Optional.of(agentTask));

    RabbitTemplate publishingTemplate = rabbitTemplate();
    OutboxPublisher publisher =
        new OutboxPublisher(
            outboxEventRepository, agentTaskRepository, publishingTemplate, properties(), CLOCK);

    publisher.publishPending();

    Message message =
        rabbitTemplate().receive(properties().queues().task(), Duration.ofSeconds(5).toMillis());
    assertThat(message).isNotNull();
    assertThat(message.getMessageProperties().getReceivedRoutingKey())
        .isEqualTo(properties().routingKeys().researchRequested());
    assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
    assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo("correlation-id");
    assertThat((String) message.getMessageProperties().getHeader("eventId"))
        .isEqualTo(eventId.toString());
    assertThat(event.status()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(event.publishedAt()).isEqualTo(NOW);
    assertThat(agentTask.status()).isEqualTo(AgentTaskStatus.QUEUED);
    verify(outboxEventRepository).save(event);
    verify(agentTaskRepository).save(agentTask);
  }

  @Test
  void rejectedTaskQueueMessageIsDeadLetteredToDlq() {
    UUID eventId = UUID.randomUUID();
    Map<String, Object> payload =
        Map.of(
            "eventId", eventId.toString(),
            "schemaVersion", "1.0",
            "correlationId", "correlation-id",
            "idempotencyKey", "idempotency-key",
            "agentTaskId", UUID.randomUUID().toString(),
            "userMessage", "Ne alayım?",
            "occurredAt", NOW.toString());
    RabbitTemplate rabbitTemplate = rabbitTemplate();
    rabbitTemplate.convertAndSend(
        properties().exchanges().topic(), properties().routingKeys().researchRequested(), payload);

    Boolean rejected =
        rabbitTemplate.execute(
            channel -> {
              GetResponse response =
                  basicGetUntilFound(channel, properties().queues().task(), Duration.ofSeconds(5));
              if (response == null) {
                return false;
              }
              channel.basicReject(response.getEnvelope().getDeliveryTag(), false);
              return true;
            });

    assertThat(rejected).isTrue();
    Message deadLetteredMessage =
        rabbitTemplate.receive(properties().queues().dlq(), Duration.ofSeconds(5).toMillis());
    assertThat(deadLetteredMessage).isNotNull();
    assertThat(deadLetteredMessage.getMessageProperties().getReceivedRoutingKey())
        .isEqualTo(properties().routingKeys().researchDeadlettered());
    Object deathHeader = deadLetteredMessage.getMessageProperties().getHeader("x-death");
    assertThat(deathHeader).isNotNull();
  }

  @Test
  void retryQueueRepublishesMessageToTaskQueueAfterDelay() {
    UUID eventId = UUID.randomUUID();
    Map<String, Object> payload =
        Map.of(
            "eventId", eventId.toString(),
            "schemaVersion", "1.0",
            "correlationId", "correlation-id",
            "idempotencyKey", "idempotency-key",
            "agentTaskId", UUID.randomUUID().toString(),
            "userMessage", "Ne alayım?",
            "occurredAt", NOW.toString());
    RabbitTemplate rabbitTemplate = rabbitTemplate();

    rabbitTemplate.convertAndSend(
        properties().exchanges().retry(), properties().routingKeys().retry1s(), payload);

    Message retriedMessage =
        rabbitTemplate.receive(properties().queues().task(), Duration.ofSeconds(5).toMillis());
    assertThat(retriedMessage).isNotNull();
    assertThat(retriedMessage.getMessageProperties().getReceivedRoutingKey())
        .isEqualTo(properties().routingKeys().researchRequested());
    Object deathHeader = retriedMessage.getMessageProperties().getHeader("x-death");
    assertThat(deathHeader).isNotNull();
  }

  private RabbitTemplate rabbitTemplate() {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
    rabbitTemplate.setMandatory(true);
    return rabbitTemplate;
  }

  private GetResponse basicGetUntilFound(
      com.rabbitmq.client.Channel channel, String queueName, Duration timeout) throws Exception {
    long deadline = System.nanoTime() + timeout.toNanos();
    GetResponse response = channel.basicGet(queueName, false);
    while (response == null && System.nanoTime() < deadline) {
      Thread.sleep(50);
      response = channel.basicGet(queueName, false);
    }
    return response;
  }

  private void declare(RabbitAdmin rabbitAdmin, Declarable declarable) {
    if (declarable instanceof Exchange exchange) {
      rabbitAdmin.declareExchange(exchange);
    } else if (declarable instanceof Queue queue) {
      rabbitAdmin.declareQueue(queue);
    } else if (declarable instanceof Binding binding) {
      rabbitAdmin.declareBinding(binding);
    }
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
