package com.financialagent.conversation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.rabbitmq")
public record AgentRabbitTopologyProperties(
    Exchanges exchanges,
    Queues queues,
    RoutingKeys routingKeys,
    Duration taskQueueTtl,
    Duration resultQueueTtl,
    Duration dlqTtl,
    int taskQueueMaxLength,
    RetryDelays retryDelays) {

  public record Exchanges(String topic, String retry, String dlq) {}

  public record Queues(
      String task, String result, String retry1s, String retry5s, String retry15s, String dlq) {}

  public record RoutingKeys(
      String researchRequested,
      String researchStarted,
      String researchProgressed,
      String researchCompleted,
      String researchFailed,
      String researchDeadlettered,
      String resultPattern,
      String retry1s,
      String retry5s,
      String retry15s) {}

  public record RetryDelays(Duration retry1s, Duration retry5s, Duration retry15s) {}
}
