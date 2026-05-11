package com.financialagent.conversation.service;

import com.financialagent.conversation.domain.AgentSseEvent;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ConversationEventStreamRegistry {

  private final Map<UUID, Set<SseEmitter>> emittersByConversationId = new ConcurrentHashMap<>();

  public SseEmitter register(UUID conversationId, long timeoutMillis) {
    SseEmitter emitter = new SseEmitter(timeoutMillis);
    emittersByConversationId
        .computeIfAbsent(conversationId, ignored -> new CopyOnWriteArraySet<>())
        .add(emitter);

    Runnable cleanup = () -> remove(conversationId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    emitter.onError(ignored -> cleanup.run());
    return emitter;
  }

  public void sendHeartbeats() {
    emittersByConversationId.forEach(
        (conversationId, emitters) ->
            emitters.forEach(emitter -> sendHeartbeat(conversationId, emitter)));
  }

  public void send(AgentSseEvent event) {
    Set<SseEmitter> emitters =
        emittersByConversationId.getOrDefault(event.conversationId(), Set.of());
    emitters.forEach(emitter -> sendEvent(event, emitter));
  }

  void replay(SseEmitter emitter, AgentSseEvent event) {
    sendEvent(event, emitter);
  }

  private void sendHeartbeat(UUID conversationId, SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event().comment("heartbeat"));
    } catch (IOException | IllegalStateException exception) {
      remove(conversationId, emitter);
      emitter.completeWithError(exception);
    }
  }

  private void sendEvent(AgentSseEvent event, SseEmitter emitter) {
    try {
      emitter.send(
          SseEmitter.event()
              .id(event.id().toString())
              .name(event.eventName())
              .data(event.payload()));
      if (event.terminal()) {
        emitter.complete();
      }
    } catch (IOException | IllegalStateException exception) {
      remove(event.conversationId(), emitter);
      emitter.completeWithError(exception);
    }
  }

  private void remove(UUID conversationId, SseEmitter emitter) {
    Set<SseEmitter> emitters = emittersByConversationId.get(conversationId);
    if (emitters == null) {
      return;
    }
    emitters.remove(emitter);
    if (emitters.isEmpty()) {
      emittersByConversationId.remove(conversationId, emitters);
    }
  }
}
