package com.financialagent.gateway.common.web;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class CorrelationIdFilter implements WebFilter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  public static final String MDC_KEY = "correlationId";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String correlationId = resolveCorrelationId(exchange.getRequest().getHeaders());
    ServerHttpRequest request =
        exchange.getRequest().mutate().header(CORRELATION_ID_HEADER, correlationId).build();
    exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

    MDC.put(MDC_KEY, correlationId);
    return chain
        .filter(exchange.mutate().request(request).build())
        .doFinally(signalType -> MDC.remove(MDC_KEY));
  }

  private String resolveCorrelationId(HttpHeaders headers) {
    String existing = headers.getFirst(CORRELATION_ID_HEADER);
    if (existing == null || existing.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return existing;
  }
}
