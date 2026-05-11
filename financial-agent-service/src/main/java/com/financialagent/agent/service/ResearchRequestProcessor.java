package com.financialagent.agent.service;

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

  public ResearchRequestProcessor(
      ProcessedAgentRequestRepository processedAgentRequestRepository,
      AgentLifecycleEventPublisher lifecycleEventPublisher,
      ResearchAgentUseCase researchAgentUseCase) {
    this.processedAgentRequestRepository = processedAgentRequestRepository;
    this.lifecycleEventPublisher = lifecycleEventPublisher;
    this.researchAgentUseCase = researchAgentUseCase;
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
      lifecycleEventPublisher.publishFailed(request, "TOOL_EXECUTION_FAILED", true);
    }
    return ProcessingResult.PROCESSED;
  }

  public enum ProcessingResult {
    PROCESSED,
    DUPLICATE
  }
}
