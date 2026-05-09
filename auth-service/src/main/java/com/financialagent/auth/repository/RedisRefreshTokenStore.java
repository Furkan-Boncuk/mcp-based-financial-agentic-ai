package com.financialagent.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialagent.auth.common.exception.ErrorCode;
import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.domain.RefreshSession;
import com.financialagent.auth.domain.RefreshSessionStatus;
import com.financialagent.auth.service.RefreshTokenStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRefreshTokenStore implements RefreshTokenStore {

  private static final String SESSION_KEY_PREFIX = "auth:refresh:session:";
  private static final String USER_SESSIONS_KEY_PREFIX = "auth:refresh:user:";
  private static final String FAMILY_SESSIONS_KEY_PREFIX = "auth:refresh:family:";
  private static final String SESSIONS_SUFFIX = ":sessions";

  private final RedisTemplate<String, String> redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration refreshTokenTtl;

  public RedisRefreshTokenStore(
      RedisTemplate<String, String> redisTemplate,
      ObjectMapper objectMapper,
      @Value("${auth.refresh-token.ttl:7d}") Duration refreshTokenTtl) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.refreshTokenTtl = refreshTokenTtl;
  }

  @Override
  public void save(RefreshSession session) {
    redisTemplate
        .opsForValue()
        .set(sessionKey(session.sessionId()), serialize(session), ttlUntilSessionExpiry(session));
    addSessionToUserSet(session.userId(), session.sessionId());
    addSessionToFamilySet(session.familyId(), session.sessionId());
  }

  @Override
  public Optional<RefreshSession> findBySessionId(UUID sessionId) {
    return Optional.ofNullable(redisTemplate.opsForValue().get(sessionKey(sessionId)))
        .map(this::deserialize);
  }

  @Override
  public void markAsRotated(UUID oldSessionId, UUID newSessionId) {
    RefreshSession existing = findExistingSession(oldSessionId);
    RefreshSession rotated =
        new RefreshSession(
            existing.userId(),
            existing.sessionId(),
            existing.tokenHash(),
            existing.familyId(),
            RefreshSessionStatus.ROTATED,
            newSessionId,
            existing.createdAt(),
            existing.expiresAt(),
            Instant.now(),
            existing.userAgent(),
            existing.ipHash());

    redisTemplate
        .opsForValue()
        .set(sessionKey(oldSessionId), serialize(rotated), ttlUntilSessionExpiry(rotated));
  }

  @Override
  public void revokeSession(UUID sessionId) {
    RefreshSession existing = findExistingSession(sessionId);
    RefreshSession revoked =
        new RefreshSession(
            existing.userId(),
            existing.sessionId(),
            existing.tokenHash(),
            existing.familyId(),
            RefreshSessionStatus.REVOKED,
            existing.rotatedToSessionId(),
            existing.createdAt(),
            existing.expiresAt(),
            existing.rotatedAt(),
            existing.userAgent(),
            existing.ipHash());

    redisTemplate
        .opsForValue()
        .set(sessionKey(sessionId), serialize(revoked), ttlUntilSessionExpiry(revoked));
  }

  @Override
  public void revokeFamily(UUID familyId) {
    findAllByFamilyId(familyId).forEach(session -> revokeSession(session.sessionId()));
  }

  @Override
  public List<RefreshSession> findAllByFamilyId(UUID familyId) {
    return findAllByIndexKey(familySessionsKey(familyId));
  }

  @Override
  public List<RefreshSession> findAllByUserId(UUID userId) {
    return findAllByIndexKey(userSessionsKey(userId));
  }

  @Override
  public void addSessionToUserSet(UUID userId, UUID sessionId) {
    String key = userSessionsKey(userId);
    redisTemplate.opsForSet().add(key, sessionId.toString());
    redisTemplate.expire(key, refreshTokenTtl);
  }

  @Override
  public void removeSessionFromUserSet(UUID userId, UUID sessionId) {
    redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId.toString());
  }

  @Override
  public void addSessionToFamilySet(UUID familyId, UUID sessionId) {
    String key = familySessionsKey(familyId);
    redisTemplate.opsForSet().add(key, sessionId.toString());
    redisTemplate.expire(key, refreshTokenTtl);
  }

  private RefreshSession findExistingSession(UUID sessionId) {
    return findBySessionId(sessionId)
        .orElseThrow(
            () ->
                new ServiceException(
                    ErrorCode.AUTH_REFRESH_REVOKED, "Refresh session could not be found"));
  }

  private List<RefreshSession> findAllByIndexKey(String indexKey) {
    return Optional.ofNullable(redisTemplate.opsForSet().members(indexKey))
        .orElseGet(Set::of)
        .stream()
        .map(UUID::fromString)
        .map(this::findBySessionId)
        .flatMap(Optional::stream)
        .toList();
  }

  private Duration ttlUntilSessionExpiry(RefreshSession session) {
    Duration ttl = Duration.between(Instant.now(), session.expiresAt());
    return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
  }

  private String serialize(RefreshSession session) {
    try {
      return objectMapper.writeValueAsString(session);
    } catch (JsonProcessingException exception) {
      throw new ServiceException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Refresh session could not be serialized");
    }
  }

  private RefreshSession deserialize(String value) {
    try {
      return objectMapper.readValue(value, RefreshSession.class);
    } catch (JsonProcessingException exception) {
      throw new ServiceException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Refresh session could not be deserialized");
    }
  }

  private String sessionKey(UUID sessionId) {
    return SESSION_KEY_PREFIX + sessionId;
  }

  private String userSessionsKey(UUID userId) {
    return USER_SESSIONS_KEY_PREFIX + userId + SESSIONS_SUFFIX;
  }

  private String familySessionsKey(UUID familyId) {
    return FAMILY_SESSIONS_KEY_PREFIX + familyId + SESSIONS_SUFFIX;
  }
}
