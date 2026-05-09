package com.financialagent.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public record GatewayRateLimitProperties(
    int perUserPerMinute,
    int perUserBurstCapacity,
    int perIpPerMinute,
    int perIpBurstCapacity,
    int messageSubmitPerMinute,
    int messageSubmitBurstCapacity) {}
