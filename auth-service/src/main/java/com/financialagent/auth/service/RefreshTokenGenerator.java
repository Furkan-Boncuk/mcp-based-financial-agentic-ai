package com.financialagent.auth.service;

import com.financialagent.auth.common.exception.ErrorCode;
import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.domain.GeneratedRefreshToken;
import com.financialagent.auth.domain.ParsedRefreshToken;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenGenerator {

  private static final int RANDOM_SECRET_BYTES = 32;
  private static final String TOKEN_SEPARATOR = ".";

  private final SecureRandom secureRandom;

  public RefreshTokenGenerator() {
    this(new SecureRandom());
  }

  RefreshTokenGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  public GeneratedRefreshToken generate() {
    UUID sessionId = UUID.randomUUID();
    byte[] randomSecret = new byte[RANDOM_SECRET_BYTES];
    secureRandom.nextBytes(randomSecret);

    String encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomSecret);
    return new GeneratedRefreshToken(
        sessionId + TOKEN_SEPARATOR + encodedSecret, sessionId, randomSecret);
  }

  public ParsedRefreshToken parse(String token) {
    if (token == null || token.isBlank()) {
      throw invalidRefreshToken();
    }

    String[] parts = token.split("\\.", -1);
    if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
      throw invalidRefreshToken();
    }

    try {
      UUID sessionId = UUID.fromString(parts[0]);
      byte[] randomSecret = Base64.getUrlDecoder().decode(parts[1]);
      if (randomSecret.length != RANDOM_SECRET_BYTES) {
        throw invalidRefreshToken();
      }
      return new ParsedRefreshToken(sessionId, randomSecret);
    } catch (IllegalArgumentException exception) {
      throw invalidRefreshToken();
    }
  }

  private ServiceException invalidRefreshToken() {
    return new ServiceException(ErrorCode.AUTH_REFRESH_REVOKED, "Invalid refresh token format");
  }
}
