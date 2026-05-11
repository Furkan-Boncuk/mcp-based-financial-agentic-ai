package com.financialagent.agent.dto;

import java.util.List;
import java.util.Map;

public record AgentResearchResult(
    String finalAnswer,
    List<Map<String, Object>> toolResults,
    Map<String, Object> tokenUsage,
    String source) {}
