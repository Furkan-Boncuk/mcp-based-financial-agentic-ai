package com.financialagent.auth.controller;

import com.financialagent.auth.common.web.CorrelationIdFilter;
import com.financialagent.auth.domain.AuthClientContext;
import com.financialagent.auth.domain.AuthTokens;
import com.financialagent.auth.domain.RefreshResult;
import com.financialagent.auth.dto.LoginRequest;
import com.financialagent.auth.dto.RefreshResponse;
import com.financialagent.auth.dto.RegisterRequest;
import com.financialagent.auth.dto.UserProfileResponse;
import com.financialagent.auth.service.AuthService;
import com.financialagent.auth.service.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class AuthController {

  static final String REFRESH_TOKEN_COOKIE = "refreshToken";

  private final AuthService authService;
  private final JwtTokenService jwtTokenService;
  private final Duration refreshTokenTtl;

  public AuthController(
      AuthService authService,
      JwtTokenService jwtTokenService,
      @Value("${auth.refresh-token.ttl:7d}") Duration refreshTokenTtl) {
    this.authService = authService;
    this.jwtTokenService = jwtTokenService;
    this.refreshTokenTtl = refreshTokenTtl;
  }

  @PostMapping("/api/v1/auth/register")
  public ResponseEntity<?> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    AuthTokens tokens = authService.register(request, clientContext(servletRequest));
    return ResponseEntity.status(HttpStatus.CREATED)
        .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString())
        .body(tokens.response());
  }

  @PostMapping("/api/v1/auth/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    AuthTokens tokens = authService.login(request, clientContext(servletRequest));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString())
        .body(tokens.response());
  }

  @PostMapping("/api/v1/auth/refresh")
  public ResponseEntity<RefreshResponse> refresh(
      @CookieValue(REFRESH_TOKEN_COOKIE) String refreshToken, HttpServletRequest servletRequest) {
    RefreshResult result = authService.refresh(refreshToken, clientContext(servletRequest));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
        .body(result.response());
  }

  @PostMapping("/api/v1/auth/logout")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          String authorizationHeader,
      @CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
      HttpServletRequest servletRequest) {
    UUID userId = jwtTokenService.requireUserId(authorizationHeader);
    authService.logout(userId, refreshToken, clientContext(servletRequest));
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
        .build();
  }

  @GetMapping("/api/v1/auth/me")
  public UserProfileResponse me(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
          String authorizationHeader) {
    UUID userId = jwtTokenService.requireUserId(authorizationHeader);
    return authService.me(userId);
  }

  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> jwks() {
    return jwtTokenService.publicJwks();
  }

  private AuthClientContext clientContext(HttpServletRequest request) {
    return new AuthClientContext(
        request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER),
        request.getHeader(HttpHeaders.USER_AGENT),
        request.getRemoteAddr());
  }

  private ResponseCookie refreshCookie(String refreshToken) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(refreshTokenTtl)
        .build();
  }

  private ResponseCookie clearRefreshCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }
}
