package com.financialagent.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.auth.common.exception.ErrorCode;
import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.domain.AuthAuditEvent;
import com.financialagent.auth.domain.AuthClientContext;
import com.financialagent.auth.domain.AuthTokens;
import com.financialagent.auth.domain.GeneratedAccessToken;
import com.financialagent.auth.domain.GeneratedRefreshToken;
import com.financialagent.auth.domain.ParsedRefreshToken;
import com.financialagent.auth.domain.RefreshResult;
import com.financialagent.auth.domain.RefreshSession;
import com.financialagent.auth.domain.RefreshSessionStatus;
import com.financialagent.auth.domain.User;
import com.financialagent.auth.dto.LoginRequest;
import com.financialagent.auth.dto.RegisterRequest;
import com.financialagent.auth.mapper.UserMapper;
import com.financialagent.auth.repository.AuthAuditEventRepository;
import com.financialagent.auth.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
  private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final byte[] RAW_SECRET = new byte[] {1, 2, 3, 4};

  @Mock private UserRepository userRepository;
  @Mock private AuthAuditEventRepository auditEventRepository;
  @Mock private JwtTokenService jwtTokenService;
  @Mock private RefreshTokenGenerator refreshTokenGenerator;
  @Mock private TokenHasher tokenHasher;
  @Mock private RefreshTokenStore refreshTokenStore;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
  private final UserMapper userMapper = new UserMapper();
  private AuthService authService;
  private AuthClientContext clientContext;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            userRepository,
            auditEventRepository,
            passwordEncoder,
            jwtTokenService,
            refreshTokenGenerator,
            tokenHasher,
            refreshTokenStore,
            userMapper,
            REFRESH_TOKEN_TTL,
            CLOCK);
    clientContext = new AuthClientContext("correlation-id", "JUnit", "127.0.0.1");
  }

  @Test
  void registerCreatesUserAndRefreshSession() {
    UUID userId = UUID.randomUUID();
    User savedUser = new User(userId, "user@example.com", "User", "stored-hash", "free");
    GeneratedRefreshToken refreshToken =
        new GeneratedRefreshToken("refresh-token", UUID.randomUUID(), RAW_SECRET);
    when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com"))
        .thenReturn(false);
    when(userRepository.save(any(User.class))).thenReturn(savedUser);
    when(jwtTokenService.generateAccessToken(any()))
        .thenReturn(
            new GeneratedAccessToken("access-token", 900, NOW.plusSeconds(900), UUID.randomUUID()));
    when(refreshTokenGenerator.generate()).thenReturn(refreshToken);
    when(tokenHasher.hash(RAW_SECRET)).thenReturn("refresh-secret-hash");

    AuthTokens tokens =
        authService.register(
            new RegisterRequest("user@example.com", "Strong1!", "User"), clientContext);

    assertThat(tokens.response().accessToken()).isEqualTo("access-token");
    assertThat(tokens.response().userId()).isEqualTo(userId);
    assertThat(tokens.refreshToken()).isEqualTo("refresh-token");

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(passwordEncoder.matches("Strong1!", userCaptor.getValue().passwordHash())).isTrue();

    ArgumentCaptor<RefreshSession> sessionCaptor = ArgumentCaptor.forClass(RefreshSession.class);
    verify(refreshTokenStore).save(sessionCaptor.capture());
    assertThat(sessionCaptor.getValue().status()).isEqualTo(RefreshSessionStatus.ACTIVE);
    assertThat(sessionCaptor.getValue().expiresAt()).isEqualTo(NOW.plus(REFRESH_TOKEN_TTL));
    assertThat(sessionCaptor.getValue().tokenHash()).isEqualTo("refresh-secret-hash");
  }

  @Test
  void registerRejectsDuplicateEmail() {
    when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com"))
        .thenReturn(true);

    assertThatThrownBy(
            () ->
                authService.register(
                    new RegisterRequest("user@example.com", "Strong1!", "User"), clientContext))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.AUTH_EMAIL_EXISTS);
  }

  @Test
  void loginRejectsInvalidCredentials() {
    User user =
        new User(
            UUID.randomUUID(),
            "user@example.com",
            "User",
            passwordEncoder.encode("Strong1!"),
            "free");
    when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com"))
        .thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> authService.login(new LoginRequest("user@example.com", "Wrong1!"), clientContext))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS);
  }

  @Test
  void refreshRotatesActiveSessionAndReturnsNewTokens() {
    UUID userId = UUID.randomUUID();
    UUID oldSessionId = UUID.randomUUID();
    UUID newSessionId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    User user = new User(userId, "user@example.com", "User", "hash", "free");
    RefreshSession oldSession =
        new RefreshSession(
            userId,
            oldSessionId,
            "stored-refresh-hash",
            familyId,
            RefreshSessionStatus.ACTIVE,
            null,
            NOW.minusSeconds(60),
            NOW.plus(REFRESH_TOKEN_TTL),
            null,
            "JUnit",
            "ip-hash");
    GeneratedRefreshToken newRefreshToken =
        new GeneratedRefreshToken("new-refresh-token", newSessionId, RAW_SECRET);
    when(refreshTokenGenerator.parse("old-refresh-token"))
        .thenReturn(new ParsedRefreshToken(oldSessionId, RAW_SECRET));
    when(refreshTokenStore.findBySessionId(oldSessionId)).thenReturn(Optional.of(oldSession));
    when(tokenHasher.matches(RAW_SECRET, "stored-refresh-hash")).thenReturn(true);
    when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
    when(refreshTokenGenerator.generate()).thenReturn(newRefreshToken);
    when(tokenHasher.hash(RAW_SECRET)).thenReturn("new-refresh-hash");
    when(jwtTokenService.generateAccessToken(any()))
        .thenReturn(
            new GeneratedAccessToken(
                "new-access-token", 900, NOW.plusSeconds(900), UUID.randomUUID()));

    RefreshResult result = authService.refresh("old-refresh-token", clientContext);

    assertThat(result.response().accessToken()).isEqualTo("new-access-token");
    assertThat(result.response().expiresIn()).isEqualTo(900);
    assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    verify(refreshTokenStore).markAsRotated(oldSessionId, newSessionId);
    verify(refreshTokenStore).save(any(RefreshSession.class));
  }

  @Test
  void refreshReuseRevokesFamilyAndWritesAuditEvent() {
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    RefreshSession rotatedSession =
        new RefreshSession(
            userId,
            sessionId,
            "stored-refresh-hash",
            familyId,
            RefreshSessionStatus.ROTATED,
            UUID.randomUUID(),
            NOW.minusSeconds(60),
            NOW.plus(REFRESH_TOKEN_TTL),
            NOW.minusSeconds(30),
            "JUnit",
            "ip-hash");
    when(refreshTokenGenerator.parse("rotated-refresh-token"))
        .thenReturn(new ParsedRefreshToken(sessionId, RAW_SECRET));
    when(refreshTokenStore.findBySessionId(sessionId)).thenReturn(Optional.of(rotatedSession));
    when(tokenHasher.matches(RAW_SECRET, "stored-refresh-hash")).thenReturn(true);

    assertThatThrownBy(() -> authService.refresh("rotated-refresh-token", clientContext))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.AUTH_REFRESH_REVOKED);

    verify(refreshTokenStore).revokeFamily(familyId);
    ArgumentCaptor<AuthAuditEvent> auditCaptor = ArgumentCaptor.forClass(AuthAuditEvent.class);
    verify(auditEventRepository).save(auditCaptor.capture());
    assertThat(auditCaptor.getValue().action()).isEqualTo("REFRESH_REUSE_DETECTED");
    assertThat(auditCaptor.getValue().errorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_REVOKED.name());
  }

  @Test
  void logoutRevokesRefreshSessionAndRemovesUserIndex() {
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    when(refreshTokenGenerator.parse("refresh-token"))
        .thenReturn(new ParsedRefreshToken(sessionId, RAW_SECRET));

    authService.logout(userId, "refresh-token", clientContext);

    verify(refreshTokenStore).revokeSession(sessionId);
    verify(refreshTokenStore).removeSessionFromUserSet(userId, sessionId);
  }

  @Test
  void redisFailureDuringLoginReturnsServiceUnavailable() {
    User user =
        new User(
            UUID.randomUUID(),
            "user@example.com",
            "User",
            passwordEncoder.encode("Strong1!"),
            "free");
    when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("user@example.com"))
        .thenReturn(Optional.of(user));
    when(jwtTokenService.generateAccessToken(any()))
        .thenReturn(
            new GeneratedAccessToken("access-token", 900, NOW.plusSeconds(900), UUID.randomUUID()));
    when(refreshTokenGenerator.generate())
        .thenReturn(new GeneratedRefreshToken("refresh-token", UUID.randomUUID(), RAW_SECRET));
    when(tokenHasher.hash(RAW_SECRET)).thenReturn("refresh-secret-hash");
    doThrow(new DataAccessResourceFailureException("redis unavailable"))
        .when(refreshTokenStore)
        .save(any(RefreshSession.class));

    assertThatThrownBy(
            () ->
                authService.login(new LoginRequest("user@example.com", "Strong1!"), clientContext))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.AUTH_REDIS_UNAVAILABLE);
  }

  @Test
  void redisFailureDuringRefreshReturnsServiceUnavailable() {
    UUID sessionId = UUID.randomUUID();
    when(refreshTokenGenerator.parse("refresh-token"))
        .thenReturn(new ParsedRefreshToken(sessionId, RAW_SECRET));
    when(refreshTokenStore.findBySessionId(sessionId))
        .thenThrow(new DataAccessResourceFailureException("redis unavailable"));

    assertThatThrownBy(() -> authService.refresh("refresh-token", clientContext))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.AUTH_REDIS_UNAVAILABLE);
  }
}
