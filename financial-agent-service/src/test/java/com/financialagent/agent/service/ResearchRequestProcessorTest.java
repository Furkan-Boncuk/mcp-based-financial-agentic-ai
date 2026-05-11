package com.financialagent.agent.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.agent.dto.ResearchRequestedEvent;
import com.financialagent.agent.repository.ProcessedAgentRequestRepository;
import java.time.Instant;
import java.util.UUID;
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

  private ResearchRequestProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new ResearchRequestProcessor(processedAgentRequestRepository, lifecycleEventPublisher);
  }

  @Test
  void processClaimsRequestAndPublishesStartedEvent() {
    ResearchRequestedEvent request = request();
    when(processedAgentRequestRepository.tryClaim(
            request.eventId(), request.idempotencyKey(), request.agentTaskId()))
        .thenReturn(true);

    ResearchRequestProcessor.ProcessingResult result = processor.process(request);

    Assertions.assertThat(result).isEqualTo(ResearchRequestProcessor.ProcessingResult.PROCESSED);
    verify(lifecycleEventPublisher).publishStarted(request);
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
        Instant.parse("2026-05-11T10:00:00Z"),
        "BIST_STOCK");
  }
}
