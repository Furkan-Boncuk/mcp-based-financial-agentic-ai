package com.financialagent.auth.common.exception;

import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ServiceException.class)
  public ProblemDetail handleServiceException(ServiceException exception) {
    return ProblemDetails.from(exception.errorCode(), exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
    ProblemDetail problemDetail =
        ProblemDetails.from(
            ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage());
    List<FieldValidationError> fieldErrors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(FieldValidationError::from)
            .toList();
    problemDetail.setProperty("fieldErrors", fieldErrors);
    return problemDetail;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception) {
    return ProblemDetails.from(
        ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage());
  }

  public record FieldValidationError(String field, String message) {
    static FieldValidationError from(FieldError fieldError) {
      return new FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }
  }
}
