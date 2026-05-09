package com.financialagent.auth.service;

import com.financialagent.auth.common.exception.ErrorCode;
import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.domain.AccessTokenClaims;
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
import com.financialagent.auth.dto.RefreshResponse;
import com.financialagent.auth.dto.RegisterRequest;
import com.financialagent.auth.dto.UserProfileResponse;
import com.financialagent.auth.mapper.UserMapper;
import com.financialagent.auth.repository.AuthAuditEventRepository;
import com.financialagent.auth.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final List<String> DEFAULT_ROLES = List.of("USER");

  private final UserRepository userRepository;
  private final AuthAuditEventRepository auditEventRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final RefreshTokenGenerator refreshTokenGenerator;
  private final TokenHasher tokenHasher;
  private final RefreshTokenStore refreshTokenStore;
  private final UserMapper userMapper;
  private final Duration refreshTokenTtl;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      AuthAuditEventRepository auditEventRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      RefreshTokenGenerator refreshTokenGenerator,
      TokenHasher tokenHasher,
      RefreshTokenStore refreshTokenStore,
      UserMapper userMapper,
      @Value("${auth.refresh-token.ttl:7d}") Duration refreshTokenTtl) {
    this(
        userRepository,
        auditEventRepository,
        passwordEncoder,
        jwtTokenService,
        refreshTokenGenerator,
        tokenHasher,
        refreshTokenStore,
        userMapper,
        refreshTokenTtl,
        Clock.systemUTC());
  }

  AuthService(
      UserRepository userRepository,
      AuthAuditEventRepository auditEventRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      RefreshTokenGenerator refreshTokenGenerator,
      TokenHasher tokenHasher,
      RefreshTokenStore refreshTokenStore,
      UserMapper userMapper,
      Duration refreshTokenTtl,
      Clock clock) {
    this.userRepository = userRepository;
    this.auditEventRepository = auditEventRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
    this.refreshTokenGenerator = refreshTokenGenerator;
    this.tokenHasher = tokenHasher;
    this.refreshTokenStore = refreshTokenStore;
    this.userMapper = userMapper;
    this.refreshTokenTtl = refreshTokenTtl;
    this.clock = clock;
  }

  @Transactional
  public AuthTokens register(RegisterRequest request, AuthClientContext clientContext) {
    if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(request.email())) {
      audit(null, "REGISTER", clientContext, false, ErrorCode.AUTH_EMAIL_EXISTS, null);
      throw new ServiceException(ErrorCode.AUTH_EMAIL_EXISTS);
    }

    User user =
        userRepository.save(
            new User(
                null,
                request.email().trim(),
                request.name().trim(),
                passwordEncoder.encode(request.password()),
                "free"));

    AuthTokens tokens = issueTokens(user, UUID.randomUUID(), clientContext);
    audit(user.id(), "REGISTER", clientContext, true, null, null);
    return tokens;
  }

  @Transactional
  public AuthTokens login(LoginRequest request, AuthClientContext clientContext) {
    User user =
        userRepository
            .findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
            .filter(
                candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
            .orElseThrow(
                () -> {
                  audit(
                      null,
                      "LOGIN",
                      clientContext,
                      false,
                      ErrorCode.AUTH_INVALID_CREDENTIALS,
                      null);
                  return new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });

    AuthTokens tokens = issueTokens(user, UUID.randomUUID(), clientContext);
    audit(user.id(), "LOGIN", clientContext, true, null, null);
    return tokens;
  }

  @Transactional
  public RefreshResult refresh(String refreshToken, AuthClientContext clientContext) {
    ParsedRefreshToken parsedToken = refreshTokenGenerator.parse(refreshToken);
    RefreshSession existing =
        withRefreshStore(
            () ->
                refreshTokenStore
                    .findBySessionId(parsedToken.sessionId())
                    .orElseThrow(() -> new ServiceException(ErrorCode.AUTH_REFRESH_REVOKED)));

    if (!tokenHasher.matches(parsedToken.randomSecret(), existing.tokenHash())) {
      revokeFamily(existing.familyId());
      audit(
          existing.userId(),
          "REFRESH_REUSE_DETECTED",
          clientContext,
          false,
          ErrorCode.AUTH_REFRESH_REVOKED,
          reuseMetadata(existing));
      throw new ServiceException(ErrorCode.AUTH_REFRESH_REVOKED);
    }

    if (existing.status() != RefreshSessionStatus.ACTIVE) {
      revokeFamily(existing.familyId());
      audit(
          existing.userId(),
          "REFRESH_REUSE_DETECTED",
          clientContext,
          false,
          ErrorCode.AUTH_REFRESH_REVOKED,
          reuseMetadata(existing));
      throw new ServiceException(ErrorCode.AUTH_REFRESH_REVOKED);
    }

    if (existing.expiresAt().isBefore(Instant.now(clock))) {
      withRefreshStore(() -> refreshTokenStore.revokeSession(existing.sessionId()));
      throw new ServiceException(ErrorCode.AUTH_TOKEN_EXPIRED);
    }

    User user =
        userRepository
            .findByIdAndDeletedAtIsNull(existing.userId())
            .orElseThrow(() -> new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS));

    GeneratedRefreshToken newRefreshToken = refreshTokenGenerator.generate();
    RefreshSession newSession =
        createRefreshSession(
            user, existing.familyId(), newRefreshToken, clientContext, Instant.now(clock));

    withRefreshStore(
        () -> {
          refreshTokenStore.markAsRotated(existing.sessionId(), newSession.sessionId());
          refreshTokenStore.save(newSession);
        });

    GeneratedAccessToken accessToken = generateAccessToken(user);
    audit(user.id(), "REFRESH", clientContext, true, null, null);
    return new RefreshResult(
        new RefreshResponse(accessToken.token(), accessToken.expiresIn()), newRefreshToken.token());
  }

  @Transactional
  public void logout(UUID userId, String refreshToken, AuthClientContext clientContext) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      ParsedRefreshToken parsedToken = refreshTokenGenerator.parse(refreshToken);
      withRefreshStore(
          () -> {
            refreshTokenStore.revokeSession(parsedToken.sessionId());
            refreshTokenStore.removeSessionFromUserSet(userId, parsedToken.sessionId());
          });
    }
    audit(userId, "LOGOUT", clientContext, true, null, null);
  }

  @Transactional(readOnly = true)
  public UserProfileResponse me(UUID userId) {
    return userRepository
        .findByIdAndDeletedAtIsNull(userId)
        .map(userMapper::toProfileResponse)
        .orElseThrow(() -> new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS));
  }

  private AuthTokens issueTokens(User user, UUID familyId, AuthClientContext clientContext) {
    GeneratedAccessToken accessToken = generateAccessToken(user);
    GeneratedRefreshToken refreshToken = refreshTokenGenerator.generate();
    RefreshSession refreshSession =
        createRefreshSession(user, familyId, refreshToken, clientContext, Instant.now(clock));
    withRefreshStore(() -> refreshTokenStore.save(refreshSession));
    return new AuthTokens(userMapper.toAuthResponse(accessToken, user), refreshToken.token());
  }

  private GeneratedAccessToken generateAccessToken(User user) {
    return jwtTokenService.generateAccessToken(
        new AccessTokenClaims(user.id(), user.email(), DEFAULT_ROLES));
  }

  private RefreshSession createRefreshSession(
      User user,
      UUID familyId,
      GeneratedRefreshToken refreshToken,
      AuthClientContext clientContext,
      Instant now) {
    return new RefreshSession(
        user.id(),
        refreshToken.sessionId(),
        tokenHasher.hash(refreshToken.randomSecret()),
        familyId,
        RefreshSessionStatus.ACTIVE,
        null,
        now,
        now.plus(refreshTokenTtl),
        null,
        truncate(clientContext.userAgent(), 512),
        ipHash(clientContext.ipAddress()));
  }

  private void revokeFamily(UUID familyId) {
    withRefreshStore(() -> refreshTokenStore.revokeFamily(familyId));
  }

  private void audit(
      UUID userId,
      String action,
      AuthClientContext clientContext,
      boolean success,
      ErrorCode errorCode,
      String metadata) {
    auditEventRepository.save(
        new AuthAuditEvent(
            userId,
            action,
            truncate(clientContext.ipAddress(), 64),
            truncate(clientContext.userAgent(), 512),
            truncate(clientContext.correlationId(), 128),
            success,
            errorCode == null ? null : errorCode.name(),
            metadata));
  }

  private String reuseMetadata(RefreshSession session) {
    return "{\"sessionId\":\""
        + session.sessionId()
        + "\",\"familyId\":\""
        + session.familyId()
        + "\",\"status\":\""
        + session.status()
        + "\"}";
  }

  private String ipHash(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank()) {
      return null;
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(ipAddress.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new ServiceException(ErrorCode.INTERNAL_SERVER_ERROR, "IP hash could not be generated");
    }
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }

  private void withRefreshStore(Runnable operation) {
    try {
      operation.run();
    } catch (DataAccessException exception) {
      throw new ServiceException(ErrorCode.AUTH_REDIS_UNAVAILABLE);
    }
  }

  private <T> T withRefreshStore(RefreshStoreOperation<T> operation) {
    try {
      return operation.execute();
    } catch (DataAccessException exception) {
      throw new ServiceException(ErrorCode.AUTH_REDIS_UNAVAILABLE);
    }
  }

  @FunctionalInterface
  private interface RefreshStoreOperation<T> {
    T execute();
  }
}
