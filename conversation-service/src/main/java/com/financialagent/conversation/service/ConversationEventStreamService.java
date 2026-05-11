package com.financialagent.conversation.service;

import com.financialagent.conversation.config.ConversationSseProperties;
import com.financialagent.conversation.domain.AgentSseEvent;
import com.financialagent.conversation.repository.AgentSseEventRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ConversationEventStreamService {

  private final ConversationAccessService conversationAccessService;
  private final ConversationEventStreamRegistry streamRegistry;
  private final AgentSseEventRepository agentSseEventRepository;
  private final ConversationSseProperties sseProperties;

  public ConversationEventStreamService(
      ConversationAccessService conversationAccessService,
      ConversationEventStreamRegistry streamRegistry,
      AgentSseEventRepository agentSseEventRepository,
      ConversationSseProperties sseProperties) {
    this.conversationAccessService = conversationAccessService;
    this.streamRegistry = streamRegistry;
    this.agentSseEventRepository = agentSseEventRepository;
    this.sseProperties = sseProperties;
  }

  public SseEmitter openConversationStream(UUID userId, UUID conversationId, String lastEventId) {
    conversationAccessService.requireOwnedConversation(userId, conversationId);
    SseEmitter emitter =
        streamRegistry.register(conversationId, sseProperties.timeout().toMillis());
    replay(conversationId, lastEventId, emitter);
    return emitter;
  }

  public void persistAndPublish(AgentSseEvent event) {
    AgentSseEvent savedEvent = agentSseEventRepository.save(event);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              streamRegistry.send(savedEvent);
            }
          });
      return;
    }
    streamRegistry.send(savedEvent);
  }

  @Scheduled(fixedDelayString = "${conversation.sse.heartbeat-interval:15s}")
  public void sendHeartbeats() {
    streamRegistry.sendHeartbeats();
  }

  private void replay(UUID conversationId, String lastEventId, SseEmitter emitter) {
    replayEvents(conversationId, lastEventId)
        .forEach(event -> streamRegistry.replay(emitter, event));
  }

  private List<AgentSseEvent> replayEvents(UUID conversationId, String lastEventId) {
    Optional<UUID> parsedLastEventId = parseLastEventId(lastEventId);
    if (parsedLastEventId.isPresent()) {
      return agentSseEventRepository
          .findByIdAndConversationId(parsedLastEventId.get(), conversationId)
          .map(
              lastEvent ->
                  agentSseEventRepository
                      .findByConversationIdAndOccurredAtAfterOrderByOccurredAtAsc(
                          conversationId, lastEvent.occurredAt()))
          .orElseGet(List::of);
    }

    List<AgentSseEvent> latestEvents =
        new ArrayList<>(
            agentSseEventRepository.findTop10ByConversationIdOrderByOccurredAtDesc(conversationId));
    Collections.reverse(latestEvents);
    if (latestEvents.size() <= sseProperties.replayLimit()) {
      return latestEvents;
    }
    return latestEvents.subList(
        latestEvents.size() - sseProperties.replayLimit(), latestEvents.size());
  }

  private Optional<UUID> parseLastEventId(String lastEventId) {
    if (lastEventId == null || lastEventId.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(lastEventId));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
