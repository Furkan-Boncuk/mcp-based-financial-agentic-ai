package com.financialagent.auth.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected auth service error"),
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
  AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
  AUTH_EMAIL_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
  AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token expired"),
  AUTH_REFRESH_REVOKED(HttpStatus.UNAUTHORIZED, "Refresh token revoked"),
  CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found");

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
