package com.financialagent.conversation.service;

import com.financialagent.conversation.config.ConversationSseProperties;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ConversationEventStreamService {

  private final ConversationAccessService conversationAccessService;
  private final ConversationEventStreamRegistry streamRegistry;
  private final ConversationSseProperties sseProperties;

  public ConversationEventStreamService(
      ConversationAccessService conversationAccessService,
      ConversationEventStreamRegistry streamRegistry,
      ConversationSseProperties sseProperties) {
    this.conversationAccessService = conversationAccessService;
    this.streamRegistry = streamRegistry;
    this.sseProperties = sseProperties;
  }

  public SseEmitter openConversationStream(UUID userId, UUID conversationId) {
    conversationAccessService.requireOwnedConversation(userId, conversationId);
    return streamRegistry.register(conversationId, sseProperties.timeout().toMillis());
  }

  @Scheduled(fixedDelayString = "${conversation.sse.heartbeat-interval:15s}")
  public void sendHeartbeats() {
    streamRegistry.sendHeartbeats();
  }
}
