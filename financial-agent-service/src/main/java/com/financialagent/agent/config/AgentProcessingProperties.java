package com.financialagent.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.processing")
public record AgentProcessingProperties(int maxAttempts) {

  public AgentProcessingProperties {
    maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
  }
}
