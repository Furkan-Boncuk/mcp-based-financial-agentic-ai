package com.financialagent.agent.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentRuntimeConfig {

  @Bean
  public Clock agentClock() {
    return Clock.systemUTC();
  }
}
