package com.financialagent.conversation.domain;

public enum OutboxEventStatus {
  PENDING,
  PUBLISHED,
  FAILED,
  DEAD_LETTERED
}
