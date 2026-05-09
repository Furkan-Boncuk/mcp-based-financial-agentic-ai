package com.financialagent.auth.dto;

public record RefreshResponse(String accessToken, long expiresIn) {}
