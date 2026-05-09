package com.financialagent.auth.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void mapsServiceExceptionToProblemDetail() {
    MDC.put("correlationId", "test-correlation-id");

    ProblemDetail result =
        handler.handleServiceException(new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS));

    assertThat(result.getStatus()).isEqualTo(401);
    assertThat(result.getTitle()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    assertThat(result.getProperties()).containsEntry("code", "AUTH_INVALID_CREDENTIALS");
    assertThat(result.getProperties()).containsEntry("correlationId", "test-correlation-id");
  }

  @Test
  void mapsValidationErrorsToFieldErrors() throws NoSuchMethodException {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(new FieldError("request", "email", "must be a valid email"));
    Method method =
        GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", Object.class);
    MethodParameter parameter = new MethodParameter(method, 0);

    ProblemDetail result =
        handler.handleValidation(new MethodArgumentNotValidException(parameter, bindingResult));

    assertThat(result.getStatus()).isEqualTo(400);
    assertThat(result.getTitle()).isEqualTo("VALIDATION_FAILED");
    assertThat(result.getProperties()).containsKey("fieldErrors");
    assertThat(result.getProperties().get("fieldErrors").toString()).contains("email");
  }

  @Test
  void mapsGenericExceptionWithoutLeakingInternalMessage() {
    ProblemDetail result =
        handler.handleUnexpected(new IllegalStateException("database password leaked"));

    assertThat(result.getStatus()).isEqualTo(500);
    assertThat(result.getTitle()).isEqualTo("INTERNAL_SERVER_ERROR");
    assertThat(result.getDetail()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage());
  }

  @SuppressWarnings("unused")
  private void dummyEndpoint(Object request) {}
}
