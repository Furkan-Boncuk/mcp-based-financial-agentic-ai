package com.financialagent.auth.service;

import com.financialagent.auth.domain.RefreshSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenStore {

  void save(RefreshSession session);

  Optional<RefreshSession> findBySessionId(UUID sessionId);

  void markAsRotated(UUID oldSessionId, UUID newSessionId);

  void revokeSession(UUID sessionId);

  void revokeFamily(UUID familyId);

  List<RefreshSession> findAllByFamilyId(UUID familyId);

  List<RefreshSession> findAllByUserId(UUID userId);

  void addSessionToUserSet(UUID userId, UUID sessionId);

  void removeSessionFromUserSet(UUID userId, UUID sessionId);

  void addSessionToFamilySet(UUID familyId, UUID sessionId);
}
