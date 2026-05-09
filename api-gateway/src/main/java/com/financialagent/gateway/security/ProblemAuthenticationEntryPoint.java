package com.financialagent.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialagent.gateway.common.exception.ErrorCode;
import com.financialagent.gateway.common.web.CorrelationIdFilter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ProblemAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public ProblemAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> commence(
      ServerWebExchange exchange, AuthenticationException authenticationException) {
    ErrorCode errorCode = errorCode(authenticationException);
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    DataBuffer buffer =
        exchange.getResponse().bufferFactory().wrap(problemBody(exchange, errorCode));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }

  private byte[] problemBody(ServerWebExchange exchange, ErrorCode errorCode) {
    Map<String, Object> body =
        Map.of(
            "type",
            "about:blank",
            "title",
            errorCode.name(),
            "status",
            errorCode.status().value(),
            "detail",
            errorCode.defaultMessage(),
            "code",
            errorCode.name(),
            "correlationId",
            correlationId(exchange));
    try {
      return objectMapper.writeValueAsBytes(body);
    } catch (JsonProcessingException exception) {
      return ("{\"title\":\"" + errorCode.name() + "\"}").getBytes(StandardCharsets.UTF_8);
    }
  }

  private ErrorCode errorCode(AuthenticationException authenticationException) {
    if (isExpiredToken(authenticationException)) {
      return ErrorCode.AUTH_TOKEN_EXPIRED;
    }
    return ErrorCode.AUTH_INVALID_CREDENTIALS;
  }

  private boolean isExpiredToken(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof OAuth2AuthenticationException oauthException) {
        String errorCode = oauthException.getError().getErrorCode();
        String description = oauthException.getError().getDescription();
        if (containsExpired(errorCode) || containsExpired(description)) {
          return true;
        }
      }
      if (containsExpired(current.getMessage())) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private boolean containsExpired(String value) {
    return value != null && value.toLowerCase().contains("expired");
  }

  private String correlationId(ServerWebExchange exchange) {
    String responseHeader =
        exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
    if (responseHeader != null && !responseHeader.isBlank()) {
      return responseHeader;
    }
    String requestHeader =
        exchange.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
    return requestHeader == null ? "" : requestHeader;
  }
}
