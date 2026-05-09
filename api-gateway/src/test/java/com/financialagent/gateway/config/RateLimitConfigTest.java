package com.financialagent.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialagent.gateway.security.AuthenticatedUser;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitConfigTest {

  private final RateLimitConfig config = new RateLimitConfig();

  @Test
  void ipKeyResolverUsesFirstForwardedForAddress() {
    ServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/auth/login")
                .header("X-Forwarded-For", "203.0.113.10, 10.0.0.4")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 51234))
                .build());

    String key = config.ipKeyResolver().resolve(exchange).block();

    assertThat(key).isEqualTo("ip:203.0.113.10");
  }

  @Test
  void userKeyResolverUsesAuthenticatedUserId() {
    AuthenticatedUser user =
        new AuthenticatedUser("user-123", "user@example.com", List.of("USER"), "jwt-1");
    TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, "token");
    authentication.setAuthenticated(true);
    ServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/conversations").build())
            .mutate()
            .principal(Mono.just(authentication))
            .build();

    String key = config.userKeyResolver().resolve(exchange).block();

    assertThat(key).isEqualTo("user:user-123");
  }

  @Test
  void userKeyResolverFallsBackToIpForUnauthenticatedRequests() {
    TestingAuthenticationToken authentication =
        new TestingAuthenticationToken("anonymous", "credentials");
    ServerWebExchange exchange =
        MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                    .remoteAddress(new InetSocketAddress("198.51.100.7", 51234))
                    .build())
            .mutate()
            .principal(Mono.just(authentication))
            .build();

    String key = config.userKeyResolver().resolve(exchange).block();

    assertThat(key).isEqualTo("ip:198.51.100.7");
  }
}
