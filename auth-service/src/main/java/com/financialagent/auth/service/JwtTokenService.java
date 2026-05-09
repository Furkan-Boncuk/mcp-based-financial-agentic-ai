package com.financialagent.auth.service;

import com.financialagent.auth.common.exception.ErrorCode;
import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.config.JwtProperties;
import com.financialagent.auth.domain.AccessTokenClaims;
import com.financialagent.auth.domain.GeneratedAccessToken;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final JwtProperties properties;
  private final JwtKeyService jwtKeyService;
  private final JwtDecoder jwtDecoder;

  public JwtTokenService(
      JwtProperties properties, JwtKeyService jwtKeyService, JwtDecoder jwtDecoder) {
    this.properties = properties;
    this.jwtKeyService = jwtKeyService;
    this.jwtDecoder = jwtDecoder;
  }

  public GeneratedAccessToken generateAccessToken(AccessTokenClaims accessTokenClaims) {
    Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
    UUID jwtId = UUID.randomUUID();

    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject(accessTokenClaims.userId().toString())
            .issuer(properties.issuer())
            .audience(properties.audience())
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .jwtID(jwtId.toString())
            .claim("userId", accessTokenClaims.userId().toString())
            .claim("email", accessTokenClaims.email())
            .claim("roles", List.copyOf(accessTokenClaims.roles()))
            .build();

    SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(jwtKeyService.signingKey().getKeyID())
                .build(),
            claims);

    try {
      signedJwt.sign(new RSASSASigner(jwtKeyService.signingKey()));
      return new GeneratedAccessToken(
          signedJwt.serialize(), properties.accessTokenTtl().toSeconds(), expiresAt, jwtId);
    } catch (JOSEException exception) {
      throw new ServiceException(
          ErrorCode.INTERNAL_SERVER_ERROR, "Access token could not be generated");
    }
  }

  public Map<String, Object> publicJwks() {
    return jwtKeyService.jwks();
  }

  public UUID requireUserId(String authorizationHeader) {
    String token = bearerToken(authorizationHeader);
    try {
      Jwt jwt = jwtDecoder.decode(token);
      return UUID.fromString(jwt.getClaimAsString("userId"));
    } catch (JwtException exception) {
      throw jwtValidationException(exception);
    } catch (IllegalArgumentException exception) {
      throw new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
  }

  private ServiceException jwtValidationException(JwtException exception) {
    if (isExpired(exception)) {
      return new ServiceException(ErrorCode.AUTH_TOKEN_EXPIRED);
    }
    return new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS);
  }

  private boolean isExpired(JwtException exception) {
    if (containsExpired(exception.getMessage())) {
      return true;
    }
    if (exception instanceof JwtValidationException validationException) {
      return validationException.getErrors().stream()
          .anyMatch(
              error ->
                  containsExpired(error.getErrorCode()) || containsExpired(error.getDescription()));
    }
    return false;
  }

  private boolean containsExpired(String value) {
    return value != null && value.toLowerCase().contains("expired");
  }

  private String bearerToken(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      throw new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    String token = authorizationHeader.substring("Bearer ".length()).trim();
    if (token.isBlank()) {
      throw new ServiceException(ErrorCode.AUTH_INVALID_CREDENTIALS);
    }
    return token;
  }
}
