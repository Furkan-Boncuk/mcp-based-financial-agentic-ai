package com.financialagent.agent.service;

import com.financialagent.agent.dto.ResearchRequestedEvent;
import com.financialagent.agent.repository.ProcessedAgentRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResearchRequestProcessor {

  private final ProcessedAgentRequestRepository processedAgentRequestRepository;
  private final AgentLifecycleEventPublisher lifecycleEventPublisher;

  public ResearchRequestProcessor(
      ProcessedAgentRequestRepository processedAgentRequestRepository,
      AgentLifecycleEventPublisher lifecycleEventPublisher) {
    this.processedAgentRequestRepository = processedAgentRequestRepository;
    this.lifecycleEventPublisher = lifecycleEventPublisher;
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
    return ProcessingResult.PROCESSED;
  }

  public enum ProcessingResult {
    PROCESSED,
    DUPLICATE
  }
}
