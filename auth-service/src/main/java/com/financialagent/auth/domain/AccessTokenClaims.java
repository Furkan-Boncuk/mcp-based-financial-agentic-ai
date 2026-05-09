package com.financialagent.auth.domain;

import java.util.List;
import java.util.UUID;

public record AccessTokenClaims(UUID userId, String email, List<String> roles) {}
