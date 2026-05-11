package com.financialagent.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.conversation.domain.AgentProgressEvent;
import com.financialagent.conversation.domain.AgentSseEvent;
import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.AgentTaskStatus;
import com.financialagent.conversation.domain.Message;
import com.financialagent.conversation.domain.MessageRole;
import com.financialagent.conversation.domain.ProcessedEvent;
import com.financialagent.conversation.repository.AgentProgressEventRepository;
import com.financialagent.conversation.repository.AgentTaskRepository;
import com.financialagent.conversation.repository.MessageRepository;
import com.financialagent.conversation.repository.ProcessedEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentResultEventServiceTest {

  private static final Instant NOW = Instant.parse("2026-05-11T08:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Mock private AgentTaskRepository agentTaskRepository;
  @Mock private MessageRepository messageRepository;
  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private AgentProgressEventRepository agentProgressEventRepository;
  @Mock private ConversationEventStreamService conversationEventStreamService;

  private AgentResultEventService service;

  @BeforeEach
  void setUp() {
    service =
        new AgentResultEventService(
            agentTaskRepository,
            messageRepository,
            processedEventRepository,
            agentProgressEventRepository,
            conversationEventStreamService,
            CLOCK);
  }

  @Test
  void startedMovesQueuedTaskToRunningAndStoresProcessedEvent() {
    AgentTask task = queuedTask();
    Map<String, Object> payload = basePayload(task);
    payload.put("startedAt", NOW.toString());
    payload.put("intentDetected", "BIST_STOCK");
    payload.put("toolsSelectedCount", 3);
    when(agentTaskRepository.findById(task.id())).thenReturn(Optional.of(task));

    service.handle("agent.research.started", payload);

    assertThat(task.status()).isEqualTo(AgentTaskStatus.RUNNING);
    assertThat(task.startedAt()).isEqualTo(NOW);
    assertThat(task.resultMetadata()).containsEntry("intentDetected", "BIST_STOCK");
    verify(agentTaskRepository).save(task);
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }

  @Test
  void progressedStoresProgressEventForSseReplay() {
    AgentTask task = runningTask();
    Map<String, Object> payload = basePayload(task);
    payload.put("stage", "ANALYZING");
    payload.put("stageDetail", "Finansal veriler analiz ediliyor");
    payload.put("occurredAt", NOW.toString());
    when(agentTaskRepository.findById(task.id())).thenReturn(Optional.of(task));

    service.handle("agent.research.progressed", payload);

    ArgumentCaptor<AgentProgressEvent> progressCaptor =
        ArgumentCaptor.forClass(AgentProgressEvent.class);
    verify(agentProgressEventRepository).save(progressCaptor.capture());
    assertThat(progressCaptor.getValue().id())
        .isEqualTo(UUID.fromString((String) payload.get("eventId")));
    assertThat(progressCaptor.getValue().agentTaskId()).isEqualTo(task.id());
    ArgumentCaptor<AgentSseEvent> sseCaptor = ArgumentCaptor.forClass(AgentSseEvent.class);
    verify(conversationEventStreamService).persistAndPublish(sseCaptor.capture());
    assertThat(sseCaptor.getValue().eventName()).isEqualTo("agent-progress");
    assertThat(sseCaptor.getValue().terminal()).isFalse();
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }

  @Test
  void completedMovesTaskToCompletedAndSavesAssistantMessage() {
    AgentTask task = runningTask();
    Map<String, Object> payload = basePayload(task);
    payload.put("finalAnswer", "Portföyünü çeşitlendirmek mantıklı görünüyor.");
    payload.put("completedAt", NOW.toString());
    payload.put("source", "mcp_tools");
    when(agentTaskRepository.findById(task.id())).thenReturn(Optional.of(task));
    when(messageRepository.existsAssistantMessageForAgentTask(task.id())).thenReturn(false);

    service.handle("agent.research.completed", payload);

    assertThat(task.status()).isEqualTo(AgentTaskStatus.COMPLETED);
    assertThat(task.completedAt()).isEqualTo(NOW);
    ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
    verify(messageRepository).save(messageCaptor.capture());
    assertThat(messageCaptor.getValue().role()).isEqualTo(MessageRole.ASSISTANT);
    assertThat(messageCaptor.getValue().content())
        .isEqualTo("Portföyünü çeşitlendirmek mantıklı görünüyor.");
    assertThat(messageCaptor.getValue().metadata())
        .containsEntry("agentTaskId", task.id().toString())
        .containsEntry("source", "mcp_tools");
    ArgumentCaptor<AgentSseEvent> sseCaptor = ArgumentCaptor.forClass(AgentSseEvent.class);
    verify(conversationEventStreamService).persistAndPublish(sseCaptor.capture());
    assertThat(sseCaptor.getValue().eventName()).isEqualTo("agent-completed");
    assertThat(sseCaptor.getValue().terminal()).isTrue();
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }

  @Test
  void duplicateCompletedEventDoesNotCreateDuplicateAssistantMessage() {
    AgentTask task = runningTask();
    Map<String, Object> payload = basePayload(task);
    payload.put("finalAnswer", "Yanıt");
    payload.put("completedAt", NOW.toString());
    when(processedEventRepository.existsByIdEventId(
            UUID.fromString((String) payload.get("eventId"))))
        .thenReturn(true);

    service.handle("agent.research.completed", payload);

    verify(agentTaskRepository, never()).findById(any());
    verify(messageRepository, never()).save(any());
    verify(conversationEventStreamService, never()).persistAndPublish(any());
    verify(processedEventRepository, never()).save(any());
  }

  @Test
  void completedDoesNotCreateAssistantMessageWhenTaskAlreadyHasOne() {
    AgentTask task = runningTask();
    Map<String, Object> payload = basePayload(task);
    payload.put("finalAnswer", "Yanıt");
    payload.put("completedAt", NOW.toString());
    when(agentTaskRepository.findById(task.id())).thenReturn(Optional.of(task));
    when(messageRepository.existsAssistantMessageForAgentTask(task.id())).thenReturn(true);

    service.handle("agent.research.completed", payload);

    assertThat(task.status()).isEqualTo(AgentTaskStatus.COMPLETED);
    verify(agentTaskRepository).save(task);
    verify(messageRepository, never()).save(any());
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }

  @Test
  void failedMovesRunningTaskToFailed() {
    AgentTask task = runningTask();
    Map<String, Object> payload = basePayload(task);
    payload.put("errorCode", "AGENT_PROVIDER_TIMEOUT");
    payload.put("errorMessage", "provider timeout");
    payload.put("failedAt", NOW.toString());
    when(agentTaskRepository.findById(task.id())).thenReturn(Optional.of(task));

    service.handle("agent.research.failed", payload);

    assertThat(task.status()).isEqualTo(AgentTaskStatus.FAILED);
    assertThat(task.errorCode()).isEqualTo("AGENT_PROVIDER_TIMEOUT");
    assertThat(task.errorMessage()).isEqualTo("provider timeout");
    verify(agentTaskRepository).save(task);
    ArgumentCaptor<AgentSseEvent> sseCaptor = ArgumentCaptor.forClass(AgentSseEvent.class);
    verify(conversationEventStreamService).persistAndPublish(sseCaptor.capture());
    assertThat(sseCaptor.getValue().eventName()).isEqualTo("agent-failed");
    assertThat(sseCaptor.getValue().terminal()).isTrue();
  }

  @Test
  void deadLetteredMovesFailedTaskToDeadLettered() {
    AgentTask task = failedTask();
    Map<String, Object> payload = basePayload(task);
    payload.put("finalErrorCode", "TOOL_EXECUTION_FAILED");
    payload.put("deadLetteredAt", NOW.toString());
    when(agentTaskRepository.findById(task.id())).thenReturn(Optional.of(task));

    service.handle("agent.research.deadlettered", payload);

    assertThat(task.status()).isEqualTo(AgentTaskStatus.DEAD_LETTERED);
    assertThat(task.errorCode()).isEqualTo("TOOL_EXECUTION_FAILED");
    verify(agentTaskRepository).save(task);
    ArgumentCaptor<AgentSseEvent> sseCaptor = ArgumentCaptor.forClass(AgentSseEvent.class);
    verify(conversationEventStreamService).persistAndPublish(sseCaptor.capture());
    assertThat(sseCaptor.getValue().eventName()).isEqualTo("agent-deadlettered");
    assertThat(sseCaptor.getValue().terminal()).isTrue();
  }

  @Test
  void invalidCompletedTransitionIsLoggedAndDoesNotPersistAssistantMessage() {
    AgentTask task = queuedTask();
    Map<String, Object> payload = basePayload(task);
    payload.put("finalAnswer", "Yanıt");
    payload.put("completedAt", NOW.toString());
    when(agentTaskRepository.findById(task.id())).thenReturn(Optional.of(task));

    service.handle("agent.research.completed", payload);

    assertThat(task.status()).isEqualTo(AgentTaskStatus.QUEUED);
    verify(agentTaskRepository, never()).save(task);
    verify(messageRepository, never()).save(any());
    verify(conversationEventStreamService, never()).persistAndPublish(any());
    verify(processedEventRepository).save(any(ProcessedEvent.class));
  }

  private AgentTask queuedTask() {
    AgentTask task = new AgentTask(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    task.markQueued();
    return task;
  }

  private AgentTask runningTask() {
    AgentTask task = queuedTask();
    task.markRunning(NOW.minusSeconds(5), Map.of());
    return task;
  }

  private AgentTask failedTask() {
    AgentTask task = runningTask();
    task.markFailed("AGENT_PROVIDER_TIMEOUT", "timeout", NOW.minusSeconds(1));
    return task;
  }

  private Map<String, Object> basePayload(AgentTask task) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", UUID.randomUUID().toString());
    payload.put("schemaVersion", "1.0");
    payload.put("correlationId", UUID.randomUUID().toString());
    payload.put("agentTaskId", task.id().toString());
    payload.put("userId", task.userId().toString());
    payload.put("conversationId", task.conversationId().toString());
    payload.put("messageId", task.messageId().toString());
    return payload;
  }
}
