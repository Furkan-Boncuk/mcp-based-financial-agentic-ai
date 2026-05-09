package com.financialagent.gateway.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected gateway error"),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
  AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
  AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token expired"),
  AUTH_REFRESH_REVOKED(HttpStatus.UNAUTHORIZED, "Refresh token revoked"),
  AUTH_EMAIL_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
  CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found"),
  CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Conversation access denied"),
  AGENT_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Agent task not found"),
  AGENT_PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Agent provider timeout"),
  TOOL_EXECUTION_FAILED(HttpStatus.BAD_GATEWAY, "Tool execution failed"),
  RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");

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
