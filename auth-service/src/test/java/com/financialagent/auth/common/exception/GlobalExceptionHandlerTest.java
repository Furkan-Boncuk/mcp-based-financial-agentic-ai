package com.financialagent.auth.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsServiceExceptionToProblemDetail() {
        ProblemDetail result = handler.handleServiceException(
                new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        assertThat(result.getStatus()).isEqualTo(401);
        assertThat(result.getTitle()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(result.getProperties()).containsEntry("code", "AUTH_INVALID_CREDENTIALS");
    }
}
