package com.financialagent.auth.dto;

import java.util.UUID;

public record AuthResponse(String accessToken, long expiresIn, UUID userId) {}
