package com.financialagent.auth.domain;

public record AuthClientContext(String correlationId, String userAgent, String ipAddress) {

  public static AuthClientContext empty() {
    return new AuthClientContext(null, null, null);
  }
}
