package com.financialagent.gateway.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected gateway error"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Authentication token expired"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found"),
    CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Conversation access denied");

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
