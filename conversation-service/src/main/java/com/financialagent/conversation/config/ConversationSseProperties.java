package com.financialagent.conversation.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conversation.sse")
public record ConversationSseProperties(Duration timeout, Duration heartbeatInterval) {

  public ConversationSseProperties {
    timeout = timeout == null ? Duration.ofMinutes(30) : timeout;
    heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(15) : heartbeatInterval;
  }
}
