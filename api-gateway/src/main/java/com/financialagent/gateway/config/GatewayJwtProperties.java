package com.financialagent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.jwt")
public record GatewayJwtProperties(String publicKey, String issuer, String audience) {}
