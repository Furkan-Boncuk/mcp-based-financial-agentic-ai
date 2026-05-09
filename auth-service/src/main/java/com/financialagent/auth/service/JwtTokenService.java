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
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final JwtProperties properties;
  private final JwtKeyService jwtKeyService;

  public JwtTokenService(JwtProperties properties, JwtKeyService jwtKeyService) {
    this.properties = properties;
    this.jwtKeyService = jwtKeyService;
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
}
