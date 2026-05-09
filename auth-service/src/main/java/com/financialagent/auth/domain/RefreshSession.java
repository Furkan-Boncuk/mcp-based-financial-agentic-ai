package com.financialagent.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshSession(
    UUID userId,
    UUID sessionId,
    String tokenHash,
    UUID familyId,
    RefreshSessionStatus status,
    UUID rotatedToSessionId,
    Instant createdAt,
    Instant expiresAt,
    Instant rotatedAt,
    String userAgent,
    String ipHash) {}
