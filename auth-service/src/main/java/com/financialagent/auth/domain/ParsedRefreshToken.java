package com.financialagent.auth.domain;

import java.util.UUID;

public record ParsedRefreshToken(UUID sessionId, byte[] randomSecret) {}
