package com.financialagent.gateway.security;

import com.financialagent.gateway.config.GatewayJwtProperties;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

@Configuration
public class SecurityConfig {

  public static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh",
    "/actuator/health",
    "/health"
  };

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http, ProblemAuthenticationEntryPoint authenticationEntryPoint) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .exceptionHandling(spec -> spec.authenticationEntryPoint(authenticationEntryPoint))
        .authorizeExchange(
            exchanges ->
                exchanges.pathMatchers(PUBLIC_PATHS).permitAll().anyExchange().authenticated())
        .oauth2ResourceServer(
            spec ->
                spec.authenticationEntryPoint(authenticationEntryPoint)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .build();
  }

  @Bean
  Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
    return jwt -> {
      List<String> roles = jwt.getClaimAsStringList("roles");
      AuthenticatedUser user =
          new AuthenticatedUser(
              jwt.getClaimAsString("userId"),
              jwt.getClaimAsString("email"),
              roles == null ? List.of() : roles,
              jwt.getId());
      List<SimpleGrantedAuthority> authorities =
          user.roles().stream()
              .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
              .map(SimpleGrantedAuthority::new)
              .toList();
      return Mono.just(
          new UsernamePasswordAuthenticationToken(user, jwt.getTokenValue(), authorities));
    };
  }

  @Bean
  ReactiveJwtDecoder jwtDecoder(GatewayJwtProperties properties) {
    NimbusReactiveJwtDecoder jwtDecoder =
        NimbusReactiveJwtDecoder.withPublicKey(publicKey(properties.publicKey()))
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

  private RSAPublicKey publicKey(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Gateway JWT public key is required");
    }
    try {
      String normalized =
          value
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s", "");
      byte[] keyBytes = Base64.getDecoder().decode(normalized);
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid gateway JWT public key configuration", exception);
    }
  }
}
