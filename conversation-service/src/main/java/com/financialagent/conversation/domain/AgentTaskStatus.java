package com.financialagent.conversation.domain;

public enum AgentTaskStatus {
  PENDING,
  QUEUED,
  RUNNING,
  COMPLETED,
  FAILED,
  DEAD_LETTERED
}
