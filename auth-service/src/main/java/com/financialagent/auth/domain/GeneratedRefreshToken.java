package com.financialagent.auth.domain;

import java.util.UUID;

public record GeneratedRefreshToken(String token, UUID sessionId, byte[] randomSecret) {}
