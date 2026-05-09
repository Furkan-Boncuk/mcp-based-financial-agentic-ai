package com.financialagent.gateway.common.exception;

public class ServiceException extends RuntimeException {

  private final ErrorCode errorCode;

  public ServiceException(ErrorCode errorCode) {
    super(errorCode.defaultMessage());
    this.errorCode = errorCode;
  }

  public ServiceException(ErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ErrorCode errorCode() {
    return errorCode;
  }
}
