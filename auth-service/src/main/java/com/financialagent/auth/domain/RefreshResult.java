package com.financialagent.auth.domain;

import com.financialagent.auth.dto.RefreshResponse;

public record RefreshResult(RefreshResponse response, String refreshToken) {}
