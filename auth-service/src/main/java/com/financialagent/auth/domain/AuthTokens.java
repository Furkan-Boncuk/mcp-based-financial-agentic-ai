package com.financialagent.auth.domain;

import com.financialagent.auth.dto.AuthResponse;

public record AuthTokens(AuthResponse response, String refreshToken) {}
