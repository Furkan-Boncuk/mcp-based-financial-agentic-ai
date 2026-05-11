package com.financialagent.agent.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.financialagent.agent.config.AgentRabbitTopologyProperties;
import com.financialagent.agent.dto.AgentProgressUpdate;
import com.financialagent.agent.dto.AgentResearchResult;
import com.financialagent.agent.dto.ResearchRequestedEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class AgentLifecycleEventPublisherTest {

  private static final Instant NOW = Instant.parse("2026-05-11T10:00:00Z");

  @Test
  void publishStartedSendsAgentStartedContractToTopicExchange() {
    RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    AgentLifecycleEventPublisher publisher =
        new AgentLifecycleEventPublisher(
            rabbitTemplate, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    ResearchRequestedEvent request = request();

    publisher.publishStarted(request);

    ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("agent.topic.exchange"), eq("agent.research.started"), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue())
        .containsEntry("schemaVersion", "1.0")
        .containsEntry("correlationId", request.correlationId())
        .containsEntry("agentTaskId", request.agentTaskId().toString())
        .containsEntry("userId", request.userId().toString())
        .containsEntry("conversationId", request.conversationId().toString())
        .containsEntry("messageId", request.messageId().toString())
        .containsEntry("startedAt", NOW.toString())
        .containsEntry("intentDetected", "BIST_STOCK")
        .containsEntry("toolsSelectedCount", 0);
    Assertions.assertThat(eventCaptor.getValue().get("eventId")).isNotNull();
  }

  @Test
  void publishProgressedSendsAgentProgressedContractToTopicExchange() {
    RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    AgentLifecycleEventPublisher publisher =
        new AgentLifecycleEventPublisher(
            rabbitTemplate, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    ResearchRequestedEvent request = request();
    AgentProgressUpdate progress =
        new AgentProgressUpdate("ANALYZING", "Analiz ediliyor", "", "stub", "RUNNING");

    publisher.publishProgressed(request, progress);

    ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("agent.topic.exchange"), eq("agent.research.progressed"), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue())
        .containsEntry("schemaVersion", "1.0")
        .containsEntry("correlationId", request.correlationId())
        .containsEntry("agentTaskId", request.agentTaskId().toString())
        .containsEntry("stage", "ANALYZING")
        .containsEntry("stageDetail", "Analiz ediliyor")
        .containsEntry("toolName", "stub")
        .containsEntry("toolStatus", "RUNNING")
        .containsEntry("occurredAt", NOW.toString());
  }

  @Test
  void publishCompletedSendsAgentCompletedContractToTopicExchange() {
    RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    AgentLifecycleEventPublisher publisher =
        new AgentLifecycleEventPublisher(
            rabbitTemplate, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    ResearchRequestedEvent request = request();
    AgentResearchResult result =
        new AgentResearchResult("Yanıt", List.of(), Map.of("totalTokens", 0), "llm_direct");

    publisher.publishCompleted(request, result);

    ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("agent.topic.exchange"), eq("agent.research.completed"), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue())
        .containsEntry("schemaVersion", "1.0")
        .containsEntry("correlationId", request.correlationId())
        .containsEntry("agentTaskId", request.agentTaskId().toString())
        .containsEntry("finalAnswer", "Yanıt")
        .containsEntry("completedAt", NOW.toString())
        .containsEntry("source", "llm_direct");
  }

  @Test
  void publishFailedSendsAgentFailedContractToTopicExchange() {
    RabbitTemplate rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
    AgentLifecycleEventPublisher publisher =
        new AgentLifecycleEventPublisher(
            rabbitTemplate, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    ResearchRequestedEvent request = request();

    publisher.publishFailed(request, "TOOL_EXECUTION_FAILED", true);

    ArgumentCaptor<Map<String, Object>> eventCaptor = ArgumentCaptor.forClass(Map.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("agent.topic.exchange"), eq("agent.research.failed"), eventCaptor.capture());
    Assertions.assertThat(eventCaptor.getValue())
        .containsEntry("schemaVersion", "1.0")
        .containsEntry("correlationId", request.correlationId())
        .containsEntry("agentTaskId", request.agentTaskId().toString())
        .containsEntry("errorCode", "TOOL_EXECUTION_FAILED")
        .containsEntry("errorMessage", "Agent processing failed")
        .containsEntry("retryable", true)
        .containsEntry("failedAt", NOW.toString())
        .containsEntry("attemptNumber", 1);
  }

  private ResearchRequestedEvent request() {
    return new ResearchRequestedEvent(
        UUID.randomUUID(),
        "1.0",
        UUID.randomUUID().toString(),
        "idempotency-key",
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "Ne alayım?",
        NOW.minusSeconds(1),
        "BIST_STOCK");
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
            Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(15)));
  }
}
