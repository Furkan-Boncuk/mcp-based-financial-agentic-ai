package com.financialagent.conversation.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class AgentRabbitTopologyConfigTest {

  private AgentRabbitTopologyConfig config;

  @BeforeEach
  void setUp() {
    AgentRabbitTopologyProperties properties =
        new AgentRabbitTopologyProperties(
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
    config = new AgentRabbitTopologyConfig(properties);
  }

  @Test
  void declaresDocumentedExchangesQueuesAndBindings() {
    Declarables declarables = config.agentRabbitDeclarables();
    Collection<Declarable> items = declarables.getDeclarables();

    assertThat(items)
        .filteredOn(TopicExchange.class::isInstance)
        .extracting(declarable -> ((TopicExchange) declarable).getName())
        .containsExactlyInAnyOrder("agent.topic.exchange", "agent.retry.exchange");
    assertThat(items)
        .filteredOn(DirectExchange.class::isInstance)
        .extracting(declarable -> ((DirectExchange) declarable).getName())
        .containsExactly("agent.dlq.exchange");
    assertThat(items)
        .filteredOn(Queue.class::isInstance)
        .extracting(declarable -> ((Queue) declarable).getName())
        .containsExactlyInAnyOrder(
            "agent.task.queue",
            "agent.result.queue",
            "agent.task.queue.retry.1s",
            "agent.task.queue.retry.5s",
            "agent.task.queue.retry.15s",
            "agent.dlq");
    assertThat(items)
        .filteredOn(Binding.class::isInstance)
        .extracting(declarable -> ((Binding) declarable).getRoutingKey())
        .containsExactlyInAnyOrder(
            "agent.research.requested",
            "agent.research.*",
            "agent.research.requested.retry.1s",
            "agent.research.requested.retry.5s",
            "agent.research.requested.retry.15s",
            "agent.research.deadlettered");
  }

  @Test
  void queuesCarryTtlDlqAndRetryArguments() {
    Queue taskQueue = queue("agent.task.queue");
    Queue resultQueue = queue("agent.result.queue");
    Queue retry1sQueue = queue("agent.task.queue.retry.1s");
    Queue retry5sQueue = queue("agent.task.queue.retry.5s");
    Queue retry15sQueue = queue("agent.task.queue.retry.15s");
    Queue dlq = queue("agent.dlq");

    assertThat(taskQueue.getArguments())
        .containsEntry("x-message-ttl", 3_600_000)
        .containsEntry("x-max-length", 10_000)
        .containsEntry("x-dead-letter-exchange", "agent.dlq.exchange")
        .containsEntry("x-dead-letter-routing-key", "agent.research.deadlettered");
    assertThat(resultQueue.getArguments()).containsEntry("x-message-ttl", 3_600_000);
    assertThat(retry1sQueue.getArguments())
        .containsEntry("x-message-ttl", 1_000)
        .containsEntry("x-dead-letter-exchange", "agent.topic.exchange")
        .containsEntry("x-dead-letter-routing-key", "agent.research.requested");
    assertThat(retry5sQueue.getArguments()).containsEntry("x-message-ttl", 5_000);
    assertThat(retry15sQueue.getArguments()).containsEntry("x-message-ttl", 15_000);
    assertThat(dlq.getArguments()).containsEntry("x-message-ttl", 604_800_000);
  }

  private Queue queue(String name) {
    return config.agentRabbitDeclarables().getDeclarables().stream()
        .filter(Queue.class::isInstance)
        .map(Queue.class::cast)
        .filter(queue -> queue.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
