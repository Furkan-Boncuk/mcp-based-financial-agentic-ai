package com.financialagent.conversation.config;

import com.financialagent.conversation.common.web.AuthenticatedUserIdArgumentResolver;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ConversationConfig implements WebMvcConfigurer {

  private final AuthenticatedUserIdArgumentResolver authenticatedUserIdArgumentResolver;

  public ConversationConfig(
      AuthenticatedUserIdArgumentResolver authenticatedUserIdArgumentResolver) {
    this.authenticatedUserIdArgumentResolver = authenticatedUserIdArgumentResolver;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(authenticatedUserIdArgumentResolver);
  }

  @Bean
  public Clock conversationClock() {
    return Clock.systemUTC();
  }
}
