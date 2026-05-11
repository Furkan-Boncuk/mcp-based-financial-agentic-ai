package com.financialagent.conversation.service;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.financialagent.conversation.config.AgentRabbitTopologyConfig;
import com.financialagent.conversation.config.AgentRabbitTopologyProperties;
import com.rabbitmq.client.Channel;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class AgentResultEventListenerRabbitMqIntegrationTest {

  @Container
  private static final RabbitMQContainer RABBITMQ =
      new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.12-management"));

  private final Jackson2JsonMessageConverter messageConverter = new Jackson2JsonMessageConverter();

  private CachingConnectionFactory connectionFactory;
  private SimpleMessageListenerContainer listenerContainer;
  private AgentResultEventService eventService;

  @BeforeEach
  void setUp() {
    connectionFactory = new CachingConnectionFactory(RABBITMQ.getHost(), RABBITMQ.getAmqpPort());
    connectionFactory.setUsername(RABBITMQ.getAdminUsername());
    connectionFactory.setPassword(RABBITMQ.getAdminPassword());

    RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
    AgentRabbitTopologyConfig config = new AgentRabbitTopologyConfig(properties());
    config
        .agentRabbitDeclarables()
        .getDeclarables()
        .forEach(declarable -> declare(rabbitAdmin, declarable));

    eventService = mock(AgentResultEventService.class);
    AgentResultEventListener listener = new AgentResultEventListener(eventService);
    listenerContainer = new SimpleMessageListenerContainer(connectionFactory);
    listenerContainer.setQueueNames(properties().queues().result());
    listenerContainer.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
    listenerContainer.setPrefetchCount(1);
    listenerContainer.setConcurrentConsumers(1);
    listenerContainer.setMessageListener(channelAwareListener(listener));
    listenerContainer.afterPropertiesSet();
    listenerContainer.start();
  }

  @AfterEach
  void tearDown() {
    if (listenerContainer != null) {
      listenerContainer.stop();
      listenerContainer.destroy();
    }
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void consumesEveryAgentResultEventTypeFromRabbitMq() {
    RabbitTemplate rabbitTemplate = rabbitTemplate();
    publish(rabbitTemplate, properties().routingKeys().researchStarted(), basePayload("startedAt"));
    publish(
        rabbitTemplate, properties().routingKeys().researchProgressed(), basePayload("occurredAt"));
    publish(rabbitTemplate, properties().routingKeys().researchCompleted(), completedPayload());
    publish(rabbitTemplate, properties().routingKeys().researchFailed(), failedPayload());
    publish(
        rabbitTemplate, properties().routingKeys().researchDeadlettered(), deadLetteredPayload());

    verify(eventService, timeout(5_000))
        .handle(eq(properties().routingKeys().researchStarted()), anyMap());
    verify(eventService, timeout(5_000))
        .handle(eq(properties().routingKeys().researchProgressed()), anyMap());
    verify(eventService, timeout(5_000))
        .handle(eq(properties().routingKeys().researchCompleted()), anyMap());
    verify(eventService, timeout(5_000))
        .handle(eq(properties().routingKeys().researchFailed()), anyMap());
    verify(eventService, timeout(5_000))
        .handle(eq(properties().routingKeys().researchDeadlettered()), anyMap());
  }

  private ChannelAwareMessageListener channelAwareListener(AgentResultEventListener listener) {
    return (Message message, Channel channel) -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> payload = (Map<String, Object>) messageConverter.fromMessage(message);
      listener.onMessage(
          payload, message.getMessageProperties().getReceivedRoutingKey(), message, channel);
    };
  }

  private void publish(
      RabbitTemplate rabbitTemplate, String routingKey, Map<String, Object> payload) {
    rabbitTemplate.convertAndSend(properties().exchanges().topic(), routingKey, payload);
  }

  private RabbitTemplate rabbitTemplate() {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(messageConverter);
    rabbitTemplate.setMandatory(true);
    return rabbitTemplate;
  }

  private Map<String, Object> basePayload(String timestampField) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", UUID.randomUUID().toString());
    payload.put("schemaVersion", "1.0");
    payload.put("correlationId", UUID.randomUUID().toString());
    payload.put("agentTaskId", UUID.randomUUID().toString());
    payload.put("userId", UUID.randomUUID().toString());
    payload.put("conversationId", UUID.randomUUID().toString());
    payload.put("messageId", UUID.randomUUID().toString());
    payload.put(timestampField, Instant.parse("2026-05-11T10:00:00Z").toString());
    return payload;
  }

  private Map<String, Object> completedPayload() {
    Map<String, Object> payload = basePayload("completedAt");
    payload.put("finalAnswer", "Yanıt hazır.");
    payload.put("source", "mcp_tools");
    return payload;
  }

  private Map<String, Object> failedPayload() {
    Map<String, Object> payload = basePayload("failedAt");
    payload.put("errorCode", "AGENT_PROVIDER_TIMEOUT");
    payload.put("errorMessage", "Provider timeout");
    payload.put("retryable", true);
    payload.put("attemptNumber", 1);
    return payload;
  }

  private Map<String, Object> deadLetteredPayload() {
    Map<String, Object> payload = basePayload("deadLetteredAt");
    payload.put("finalErrorCode", "TOOL_EXECUTION_FAILED");
    payload.put("attempts", 3);
    return payload;
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
