package com.financialagent.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
    UUID userId, String email, String name, String tier, Instant createdAt) {}
