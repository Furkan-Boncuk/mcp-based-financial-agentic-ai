package com.financialagent.agent.service;

import com.financialagent.agent.config.AgentProcessingProperties;
import com.financialagent.agent.dto.AgentResearchResult;
import com.financialagent.agent.dto.ResearchRequestedEvent;
import com.financialagent.agent.repository.ProcessedAgentRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchRequestProcessor {

  private final ProcessedAgentRequestRepository processedAgentRequestRepository;
  private final AgentLifecycleEventPublisher lifecycleEventPublisher;
  private final ResearchAgentUseCase researchAgentUseCase;
  private final AgentProcessingProperties processingProperties;

  public ResearchRequestProcessor(
      ProcessedAgentRequestRepository processedAgentRequestRepository,
      AgentLifecycleEventPublisher lifecycleEventPublisher,
      ResearchAgentUseCase researchAgentUseCase,
      AgentProcessingProperties processingProperties) {
    this.processedAgentRequestRepository = processedAgentRequestRepository;
    this.lifecycleEventPublisher = lifecycleEventPublisher;
    this.researchAgentUseCase = researchAgentUseCase;
    this.processingProperties = processingProperties;
  }

  @Transactional
  public ProcessingResult process(ResearchRequestedEvent request) {
    boolean claimed =
        processedAgentRequestRepository.tryClaim(
            request.eventId(), request.idempotencyKey(), request.agentTaskId());
    if (!claimed) {
      return ProcessingResult.DUPLICATE;
    }
    lifecycleEventPublisher.publishStarted(request);
    try {
      AgentResearchResult result =
          researchAgentUseCase.execute(
              request, progress -> lifecycleEventPublisher.publishProgressed(request, progress));
      lifecycleEventPublisher.publishCompleted(request, result);
    } catch (Exception exception) {
      handleFailure(request);
    }
    return ProcessingResult.PROCESSED;
  }

  private void handleFailure(ResearchRequestedEvent request) {
    if (request.attemptNumber() < processingProperties.maxAttempts()) {
      lifecycleEventPublisher.publishFailed(request, "TOOL_EXECUTION_FAILED", true);
      lifecycleEventPublisher.publishRetry(request);
      return;
    }
    lifecycleEventPublisher.publishFailed(request, "TOOL_EXECUTION_FAILED", false);
    lifecycleEventPublisher.publishDeadLettered(request, "TOOL_EXECUTION_FAILED");
  }

  public enum ProcessingResult {
    PROCESSED,
    DUPLICATE
  }
}
