package com.financialagent.agent.service;

import com.financialagent.agent.dto.AgentProgressUpdate;
import com.financialagent.agent.dto.AgentResearchResult;
import com.financialagent.agent.dto.ResearchRequestedEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class StubResearchAgentUseCase implements ResearchAgentUseCase {

  @Override
  public AgentResearchResult execute(
      ResearchRequestedEvent request, Consumer<AgentProgressUpdate> progressCallback) {
    progressCallback.accept(
        new AgentProgressUpdate(
            "ANALYZING",
            "Finansal araştırma isteği işleniyor.",
            "",
            "stub_research_agent",
            "RUNNING"));
    return new AgentResearchResult(
        "Araştırma isteği alındı. Detaylı analiz motoru sonraki fazda bağlanacak.",
        List.of(),
        Map.of("promptTokens", 0, "completionTokens", 0, "totalTokens", 0),
        "llm_direct");
  }
}
