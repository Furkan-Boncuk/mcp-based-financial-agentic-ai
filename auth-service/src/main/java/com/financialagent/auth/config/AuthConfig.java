package com.financialagent.auth.config;

import com.financialagent.auth.service.JwtKeyService;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class AuthConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  SecureRandom secureRandom() {
    return new SecureRandom();
  }

  @Bean
  JwtDecoder jwtDecoder(JwtProperties properties, JwtKeyService jwtKeyService) {
    RSAPublicKey publicKey = jwtKeyService.publicKey();
    NimbusJwtDecoder jwtDecoder =
        NimbusJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)
            .build();
    jwtDecoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()),
            audienceValidator(properties.audience()),
            userIdValidator()));
    return jwtDecoder;
  }

  private OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
    return jwt -> {
      if (jwt.getAudience().contains(audience)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Required JWT audience is missing", null));
    };
  }

  private OAuth2TokenValidator<Jwt> userIdValidator() {
    return jwt -> {
      String userId = jwt.getClaimAsString("userId");
      if (userId != null && !userId.isBlank()) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "Required JWT userId claim is missing", null));
    };
  }
}
