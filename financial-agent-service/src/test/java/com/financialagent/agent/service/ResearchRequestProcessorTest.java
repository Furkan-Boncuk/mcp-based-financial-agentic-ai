package com.financialagent.agent.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.agent.config.AgentProcessingProperties;
import com.financialagent.agent.dto.AgentProgressUpdate;
import com.financialagent.agent.dto.AgentResearchResult;
import com.financialagent.agent.dto.ResearchRequestedEvent;
import com.financialagent.agent.repository.ProcessedAgentRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResearchRequestProcessorTest {

  @Mock private ProcessedAgentRequestRepository processedAgentRequestRepository;
  @Mock private AgentLifecycleEventPublisher lifecycleEventPublisher;
  @Mock private ResearchAgentUseCase researchAgentUseCase;

  private ResearchRequestProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new ResearchRequestProcessor(
            processedAgentRequestRepository,
            lifecycleEventPublisher,
            researchAgentUseCase,
            new AgentProcessingProperties(3));
  }

  @Test
  void processClaimsRequestAndPublishesLifecycleEvents() {
    ResearchRequestedEvent request = request();
    AgentResearchResult agentResult =
        new AgentResearchResult("Yanıt", List.of(), Map.of("totalTokens", 0), "llm_direct");
    when(processedAgentRequestRepository.tryClaim(
            request.eventId(), request.idempotencyKey(), request.agentTaskId()))
        .thenReturn(true);
    when(researchAgentUseCase.execute(eq(request), any())).thenReturn(agentResult);

    ResearchRequestProcessor.ProcessingResult processingResult = processor.process(request);

    Assertions.assertThat(processingResult)
        .isEqualTo(ResearchRequestProcessor.ProcessingResult.PROCESSED);
    verify(lifecycleEventPublisher).publishStarted(request);
    verify(researchAgentUseCase).execute(eq(request), any());
    verify(lifecycleEventPublisher).publishCompleted(request, agentResult);
  }

  @Test
  void progressCallbackPublishesProgressedEvent() {
    ResearchRequestedEvent request = request();
    AgentResearchResult result =
        new AgentResearchResult("Yanıt", List.of(), Map.of("totalTokens", 0), "llm_direct");
    AgentProgressUpdate progress =
        new AgentProgressUpdate("ANALYZING", "Analiz ediliyor", "", "stub", "RUNNING");
    when(processedAgentRequestRepository.tryClaim(
            request.eventId(), request.idempotencyKey(), request.agentTaskId()))
        .thenReturn(true);
    when(researchAgentUseCase.execute(eq(request), any()))
        .thenAnswer(
            invocation -> {
              Consumer<AgentProgressUpdate> callback = invocation.getArgument(1);
              callback.accept(progress);
              return result;
            });

    processor.process(request);

    verify(lifecycleEventPublisher).publishProgressed(request, progress);
  }

  @Test
  void retryableProcessingFailurePublishesAgentFailedAndRetry() {
    ResearchRequestedEvent request = request(1);
    when(processedAgentRequestRepository.tryClaim(
            request.eventId(), request.idempotencyKey(), request.agentTaskId()))
        .thenReturn(true);
    when(researchAgentUseCase.execute(eq(request), any()))
        .thenThrow(new IllegalStateException("boom"));

    ResearchRequestProcessor.ProcessingResult result = processor.process(request);

    Assertions.assertThat(result).isEqualTo(ResearchRequestProcessor.ProcessingResult.PROCESSED);
    verify(lifecycleEventPublisher).publishStarted(request);
    verify(lifecycleEventPublisher).publishFailed(request, "TOOL_EXECUTION_FAILED", true);
    verify(lifecycleEventPublisher).publishRetry(request);
    verify(lifecycleEventPublisher, never()).publishCompleted(eq(request), any());
    verify(lifecycleEventPublisher, never()).publishDeadLettered(eq(request), any());
  }

  @Test
  void maxAttemptFailurePublishesAgentFailedAndDeadLettered() {
    ResearchRequestedEvent request = request(3);
    when(processedAgentRequestRepository.tryClaim(
            request.eventId(), request.idempotencyKey(), request.agentTaskId()))
        .thenReturn(true);
    when(researchAgentUseCase.execute(eq(request), any()))
        .thenThrow(new IllegalStateException("boom"));

    ResearchRequestProcessor.ProcessingResult result = processor.process(request);

    Assertions.assertThat(result).isEqualTo(ResearchRequestProcessor.ProcessingResult.PROCESSED);
    verify(lifecycleEventPublisher).publishStarted(request);
    verify(lifecycleEventPublisher).publishFailed(request, "TOOL_EXECUTION_FAILED", false);
    verify(lifecycleEventPublisher).publishDeadLettered(request, "TOOL_EXECUTION_FAILED");
    verify(lifecycleEventPublisher, never()).publishRetry(request);
  }

  @Test
  void duplicateRequestIsIgnoredWithoutPublishingStartedEvent() {
    ResearchRequestedEvent request = request();
    when(processedAgentRequestRepository.tryClaim(
            request.eventId(), request.idempotencyKey(), request.agentTaskId()))
        .thenReturn(false);

    ResearchRequestProcessor.ProcessingResult result = processor.process(request);

    Assertions.assertThat(result).isEqualTo(ResearchRequestProcessor.ProcessingResult.DUPLICATE);
    verify(lifecycleEventPublisher, never()).publishStarted(request);
    verify(researchAgentUseCase, never()).execute(eq(request), any());
  }

  private ResearchRequestedEvent request() {
    return request(1);
  }

  private ResearchRequestedEvent request(int attemptNumber) {
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
        Instant.parse("2026-05-11T10:00:00Z"),
        "BIST_STOCK",
        attemptNumber);
  }
}
