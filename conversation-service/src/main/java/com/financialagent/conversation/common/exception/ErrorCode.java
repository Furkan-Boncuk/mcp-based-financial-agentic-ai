package com.financialagent.conversation.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected conversation service error"),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
  AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
  CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found"),
  CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Conversation access denied"),
  AGENT_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Agent task not found");

  private final HttpStatus status;
  private final String defaultMessage;

  ErrorCode(HttpStatus status, String defaultMessage) {
    this.status = status;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus status() {
    return status;
  }

  public String defaultMessage() {
    return defaultMessage;
  }
}
