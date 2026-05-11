package com.financialagent.agent.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentProcessingProperties.class)
public class AgentRuntimeConfig {

  @Bean
  public Clock agentClock() {
    return Clock.systemUTC();
  }
}
