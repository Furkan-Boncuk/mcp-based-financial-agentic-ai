package com.financialagent.gateway.config;

import com.financialagent.gateway.security.AuthenticatedUser;
import java.net.InetSocketAddress;
import java.security.Principal;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

  private static final int SECONDS_PER_MINUTE = 60;
  private static final String X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String UNKNOWN_CLIENT = "unknown";

  @Bean("ipKeyResolver")
  KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(ipKey(exchange));
  }

  @Bean("userKeyResolver")
  KeyResolver userKeyResolver() {
    return exchange ->
        exchange
            .getPrincipal()
            .map(this::userKey)
            .filter(key -> !key.isBlank())
            .switchIfEmpty(Mono.fromSupplier(() -> ipKey(exchange)));
  }

  @Bean("ipRateLimiter")
  RedisRateLimiter ipRateLimiter(GatewayRateLimitProperties properties) {
    return rateLimiter(properties.perIpPerMinute(), properties.perIpBurstCapacity());
  }

  @Bean("userRateLimiter")
  RedisRateLimiter userRateLimiter(GatewayRateLimitProperties properties) {
    return rateLimiter(properties.perUserPerMinute(), properties.perUserBurstCapacity());
  }

  @Bean("messageSubmitRateLimiter")
  RedisRateLimiter messageSubmitRateLimiter(GatewayRateLimitProperties properties) {
    return rateLimiter(
        properties.messageSubmitPerMinute(), properties.messageSubmitBurstCapacity());
  }

  private RedisRateLimiter rateLimiter(int perMinute, int burstCapacity) {
    int replenishRate = Math.max(1, (int) Math.ceil(perMinute / (double) SECONDS_PER_MINUTE));
    return new RedisRateLimiter(replenishRate, Math.max(1, burstCapacity), 1);
  }

  private String userKey(Principal principal) {
    if (principal instanceof Authentication authentication) {
      if (!authentication.isAuthenticated()) {
        return "";
      }
      Object principalValue = authentication.getPrincipal();
      if (principalValue instanceof AuthenticatedUser user && hasText(user.userId())) {
        return "user:" + user.userId();
      }
      if (hasText(authentication.getName())) {
        return "user:" + authentication.getName();
      }
    }
    return "";
  }

  private String ipKey(ServerWebExchange exchange) {
    return "ip:" + clientIp(exchange);
  }

  private String clientIp(ServerWebExchange exchange) {
    String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
    if (hasText(forwardedFor)) {
      String firstForwardedIp = forwardedFor.split(",", 2)[0].trim();
      if (hasText(firstForwardedIp)) {
        return firstForwardedIp;
      }
    }

    InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
    if (remoteAddress != null && remoteAddress.getAddress() != null) {
      return remoteAddress.getAddress().getHostAddress();
    }
    return UNKNOWN_CLIENT;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
