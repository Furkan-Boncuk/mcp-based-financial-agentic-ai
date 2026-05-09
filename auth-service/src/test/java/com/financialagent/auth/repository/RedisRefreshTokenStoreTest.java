package com.financialagent.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financialagent.auth.domain.RefreshSession;
import com.financialagent.auth.domain.RefreshSessionStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

  private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

  @Mock private RedisTemplate<String, String> redisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @Mock private SetOperations<String, String> setOperations;

  private final ObjectMapper objectMapper =
      JsonMapper.builder().addModule(new JavaTimeModule()).build();

  private RedisRefreshTokenStore store;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
    store = new RedisRefreshTokenStore(redisTemplate, objectMapper, REFRESH_TOKEN_TTL);
  }

  @Test
  void savePersistsActiveSessionWithIndexesAndTtl() throws Exception {
    RefreshSession session = activeSession();

    store.save(session);

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations)
        .set(eq(sessionKey(session.sessionId())), jsonCaptor.capture(), any(Duration.class));
    RefreshSession stored = objectMapper.readValue(jsonCaptor.getValue(), RefreshSession.class);

    assertThat(stored.status()).isEqualTo(RefreshSessionStatus.ACTIVE);
    assertThat(stored.sessionId()).isEqualTo(session.sessionId());
    verify(setOperations).add(userSessionsKey(session.userId()), session.sessionId().toString());
    verify(setOperations)
        .add(familySessionsKey(session.familyId()), session.sessionId().toString());
    verify(redisTemplate).expire(userSessionsKey(session.userId()), REFRESH_TOKEN_TTL);
    verify(redisTemplate).expire(familySessionsKey(session.familyId()), REFRESH_TOKEN_TTL);
  }

  @Test
  void findBySessionIdUsesDirectSessionKey() throws Exception {
    RefreshSession session = activeSession();
    when(valueOperations.get(sessionKey(session.sessionId())))
        .thenReturn(objectMapper.writeValueAsString(session));

    Optional<RefreshSession> result = store.findBySessionId(session.sessionId());

    assertThat(result).contains(session);
    verify(valueOperations).get(sessionKey(session.sessionId()));
    verify(redisTemplate, never()).keys(anyString());
  }

  @Test
  void markAsRotatedKeepsOldSessionAsRotatedTombstone() throws Exception {
    RefreshSession session = activeSession();
    UUID newSessionId = UUID.randomUUID();
    when(valueOperations.get(sessionKey(session.sessionId())))
        .thenReturn(objectMapper.writeValueAsString(session));

    store.markAsRotated(session.sessionId(), newSessionId);

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations)
        .set(eq(sessionKey(session.sessionId())), jsonCaptor.capture(), any(Duration.class));
    RefreshSession rotated = objectMapper.readValue(jsonCaptor.getValue(), RefreshSession.class);

    assertThat(rotated.status()).isEqualTo(RefreshSessionStatus.ROTATED);
    assertThat(rotated.rotatedToSessionId()).isEqualTo(newSessionId);
    assertThat(rotated.rotatedAt()).isNotNull();
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  void revokeFamilyRevokesEverySessionInFamilyWithoutPatternScanning() throws Exception {
    UUID familyId = UUID.randomUUID();
    RefreshSession first = activeSession(familyId);
    RefreshSession second = activeSession(familyId);
    when(setOperations.members(familySessionsKey(familyId)))
        .thenReturn(Set.of(first.sessionId().toString(), second.sessionId().toString()));
    when(valueOperations.get(sessionKey(first.sessionId())))
        .thenReturn(objectMapper.writeValueAsString(first));
    when(valueOperations.get(sessionKey(second.sessionId())))
        .thenReturn(objectMapper.writeValueAsString(second));

    store.revokeFamily(familyId);

    ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations)
        .set(eq(sessionKey(first.sessionId())), jsonCaptor.capture(), any(Duration.class));
    verify(valueOperations)
        .set(eq(sessionKey(second.sessionId())), jsonCaptor.capture(), any(Duration.class));

    List<RefreshSession> revokedSessions =
        jsonCaptor.getAllValues().stream()
            .map(this::readSession)
            .filter(session -> session.familyId().equals(familyId))
            .toList();
    assertThat(revokedSessions)
        .extracting(RefreshSession::status)
        .containsOnly(RefreshSessionStatus.REVOKED);
    verify(redisTemplate, never()).keys(anyString());
  }

  @Test
  void findAllByUserIdUsesUserIndexAndDirectSessionLookups() throws Exception {
    RefreshSession session = activeSession();
    when(setOperations.members(userSessionsKey(session.userId())))
        .thenReturn(Set.of(session.sessionId().toString()));
    when(valueOperations.get(sessionKey(session.sessionId())))
        .thenReturn(objectMapper.writeValueAsString(session));

    List<RefreshSession> result = store.findAllByUserId(session.userId());

    assertThat(result).containsExactly(session);
    verify(valueOperations).get(sessionKey(session.sessionId()));
    verify(redisTemplate, never()).keys(anyString());
  }

  @Test
  void removeSessionFromUserSetRemovesOnlyTheIndexedSession() {
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    store.removeSessionFromUserSet(userId, sessionId);

    verify(setOperations).remove(userSessionsKey(userId), sessionId.toString());
    verify(redisTemplate, never()).keys(anyString());
  }

  private RefreshSession readSession(String json) {
    try {
      return objectMapper.readValue(json, RefreshSession.class);
    } catch (Exception exception) {
      throw new AssertionError("RefreshSession JSON should be readable", exception);
    }
  }

  private RefreshSession activeSession() {
    return activeSession(UUID.randomUUID());
  }

  private RefreshSession activeSession(UUID familyId) {
    return new RefreshSession(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "stored-token-hash",
        familyId,
        RefreshSessionStatus.ACTIVE,
        null,
        Instant.now(),
        Instant.now().plus(REFRESH_TOKEN_TTL),
        null,
        "JUnit",
        "ip-hash");
  }

  private String sessionKey(UUID sessionId) {
    return "auth:refresh:session:" + sessionId;
  }

  private String userSessionsKey(UUID userId) {
    return "auth:refresh:user:" + userId + ":sessions";
  }

  private String familySessionsKey(UUID familyId) {
    return "auth:refresh:family:" + familyId + ":sessions";
  }
}
