package com.financialagent.agent.common.exception;

import java.net.URI;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

public final class ProblemDetails {

  private ProblemDetails() {}

  public static ProblemDetail from(ErrorCode errorCode, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
    problemDetail.setTitle(errorCode.name());
    problemDetail.setType(URI.create("urn:financial-agent:error:" + errorCode.name()));
    problemDetail.setProperty("code", errorCode.name());
    problemDetail.setProperty("correlationId", MDC.get("correlationId"));
    problemDetail.setProperty("timestamp", Instant.now().toString());
    return problemDetail;
  }
}
