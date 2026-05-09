package com.financialagent.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record GeneratedAccessToken(String token, long expiresIn, Instant expiresAt, UUID jwtId) {}
