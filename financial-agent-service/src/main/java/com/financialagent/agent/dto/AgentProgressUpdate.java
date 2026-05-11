package com.financialagent.agent.dto;

public record AgentProgressUpdate(
    String stage, String stageDetail, String partialContent, String toolName, String toolStatus) {}
