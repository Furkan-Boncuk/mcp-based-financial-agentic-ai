package com.financialagent.gateway.security;

import java.util.List;

public record AuthenticatedUser(String userId, String email, List<String> roles, String jwtId) {}
