package com.financialagent.agent.service;

import com.financialagent.agent.dto.AgentProgressUpdate;
import com.financialagent.agent.dto.AgentResearchResult;
import com.financialagent.agent.dto.ResearchRequestedEvent;
import java.util.function.Consumer;

public interface ResearchAgentUseCase {

  AgentResearchResult execute(
      ResearchRequestedEvent request, Consumer<AgentProgressUpdate> progressCallback);
}
