package com.financialagent.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
    String privateKey,
    String publicKey,
    String issuer,
    String audience,
    Duration accessTokenTtl,
    String keyId) {}
