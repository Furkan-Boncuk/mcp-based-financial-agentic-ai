package com.financialagent.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financialagent.auth.common.exception.ServiceException;
import com.financialagent.auth.config.JwtProperties;
import com.financialagent.auth.config.TestJwtKeys;
import com.financialagent.auth.domain.AccessTokenClaims;
import com.financialagent.auth.domain.GeneratedAccessToken;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  private static final String ISSUER = "https://auth.test";
  private static final String AUDIENCE = "https://api.test";
  private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
  private static final String KEY_ID = "test-key-id";

  @Test
  void generatesRs256AccessTokenWithRequiredClaims() throws Exception {
    JwtKeyService keyService = jwtKeyService(jwtProperties());
    JwtTokenService tokenService = new JwtTokenService(jwtProperties(), keyService);
    UUID userId = UUID.randomUUID();

    GeneratedAccessToken generated =
        tokenService.generateAccessToken(
            new AccessTokenClaims(userId, "user@example.com", List.of("USER", "PREMIUM")));
    SignedJWT jwt = SignedJWT.parse(generated.token());

    assertThat(jwt.verify(new RSASSAVerifier(keyService.signingKey().toPublicJWK()))).isTrue();
    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(KEY_ID);
    assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
    assertThat(jwt.getJWTClaimsSet().getStringClaim("userId")).isEqualTo(userId.toString());
    assertThat(jwt.getJWTClaimsSet().getStringClaim("email")).isEqualTo("user@example.com");
    assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles"))
        .containsExactly("USER", "PREMIUM");
    assertThat(jwt.getJWTClaimsSet().getJWTID()).isEqualTo(generated.jwtId().toString());
    assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(ISSUER);
    assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(AUDIENCE);
    assertThat(generated.expiresIn()).isEqualTo(900);
  }

  @Test
  void accessTokenExpiresAfterConfiguredTtl() throws Exception {
    JwtKeyService keyService = jwtKeyService(jwtProperties());
    JwtTokenService tokenService = new JwtTokenService(jwtProperties(), keyService);

    GeneratedAccessToken generated =
        tokenService.generateAccessToken(
            new AccessTokenClaims(UUID.randomUUID(), "user@example.com", List.of("USER")));
    SignedJWT jwt = SignedJWT.parse(generated.token());

    long secondsBetweenClaims =
        Duration.between(
                jwt.getJWTClaimsSet().getIssueTime().toInstant(),
                jwt.getJWTClaimsSet().getExpirationTime().toInstant())
            .toSeconds();
    assertThat(secondsBetweenClaims).isEqualTo(ACCESS_TOKEN_TTL.toSeconds());
    assertThat(generated.expiresAt())
        .isEqualTo(jwt.getJWTClaimsSet().getExpirationTime().toInstant());
  }

  @Test
  void exposesPublicJwksWithoutPrivateKeyMaterial() {
    JwtKeyService keyService = jwtKeyService(jwtProperties());
    JwtTokenService tokenService = new JwtTokenService(jwtProperties(), keyService);

    Map<String, Object> jwks = tokenService.publicJwks();
    List<?> keys = (List<?>) jwks.get("keys");
    Map<?, ?> publicKey = (Map<?, ?>) keys.get(0);

    assertThat(jwks).containsKey("keys");
    assertThat(publicKey.containsKey("d")).isFalse();
    assertThat(publicKey.containsKey("p")).isFalse();
    assertThat(publicKey.containsKey("q")).isFalse();
    assertThat(jwks.toString()).contains(KEY_ID);
  }

  @Test
  void invalidKeyConfigFailsStartupClearly() {
    JwtProperties invalidProperties =
        new JwtProperties(
            "not-a-private-key", "not-a-public-key", ISSUER, AUDIENCE, ACCESS_TOKEN_TTL, KEY_ID);
    JwtKeyService keyService = new JwtKeyService(invalidProperties);

    assertThatThrownBy(keyService::initialize)
        .isInstanceOf(ServiceException.class)
        .hasMessage("Invalid JWT key configuration")
        .message()
        .doesNotContain("not-a-private-key")
        .doesNotContain("not-a-public-key");
  }

  private JwtKeyService jwtKeyService(JwtProperties properties) {
    JwtKeyService keyService = new JwtKeyService(properties);
    keyService.initialize();
    return keyService;
  }

  private JwtProperties jwtProperties() {
    return new JwtProperties(
        TestJwtKeys.privateKey(),
        TestJwtKeys.publicKey(),
        ISSUER,
        AUDIENCE,
        ACCESS_TOKEN_TTL,
        KEY_ID);
  }
}
