package com.financialagent.conversation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.GlobalExceptionHandler;
import com.financialagent.conversation.common.exception.ServiceException;
import com.financialagent.conversation.common.web.AuthenticatedUserIdArgumentResolver;
import com.financialagent.conversation.dto.AgentTaskRefResponse;
import com.financialagent.conversation.dto.AgentTaskResponse;
import com.financialagent.conversation.dto.ConversationResponse;
import com.financialagent.conversation.dto.MessageResponse;
import com.financialagent.conversation.service.ConversationEventStreamService;
import com.financialagent.conversation.service.ConversationService;
import com.financialagent.conversation.service.MessageSubmitService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

  private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

  @Mock private ConversationService conversationService;
  @Mock private MessageSubmitService messageSubmitService;
  @Mock private ConversationEventStreamService conversationEventStreamService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ConversationController(
                    conversationService, messageSubmitService, conversationEventStreamService))
            .setValidator(validator)
            .setCustomArgumentResolvers(
                new AuthenticatedUserIdArgumentResolver(),
                new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void createConversationReturnsCreatedDto() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationService.createConversation(eq(userId), any()))
        .thenReturn(
            new ConversationResponse(
                conversationId, "Portfolio", "Long term ideas", userId, NOW, NOW));

    mockMvc
        .perform(
            post("/api/v1/conversations")
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Portfolio",
                      "description": "Long term ideas"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/conversations/" + conversationId))
        .andExpect(jsonPath("$.id").value(conversationId.toString()))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.title").value("Portfolio"));
  }

  @Test
  void createConversationRejectsBlankTitleBeforeServiceCall() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/conversations")
                .header(
                    AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER,
                    UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": " "
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
  }

  @Test
  void missingAuthUserHeaderReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/conversations"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void invalidAuthUserHeaderReturnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/conversations")
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, "not-a-uuid"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.title").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void listConversationsReturnsPageOwnedByHeaderUser() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationService.listConversations(eq(userId), any()))
        .thenReturn(
            new PageImpl<>(
                java.util.List.of(
                    new ConversationResponse(
                        conversationId, "Watchlist", null, userId, NOW, NOW))));

    mockMvc
        .perform(
            get("/api/v1/conversations")
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(conversationId.toString()))
        .andExpect(jsonPath("$.content[0].userId").value(userId.toString()));
  }

  @Test
  void getConversationMapsAccessDeniedToForbidden() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationService.getConversation(userId, conversationId))
        .thenThrow(new ServiceException(ErrorCode.CONVERSATION_ACCESS_DENIED));

    mockMvc
        .perform(
            get("/api/v1/conversations/{conversationId}", conversationId)
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("CONVERSATION_ACCESS_DENIED"));
  }

  @Test
  void listMessagesReturnsMessagePage() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    when(conversationService.listMessages(eq(userId), eq(conversationId), any()))
        .thenReturn(
            new PageImpl<>(
                java.util.List.of(
                    new MessageResponse(
                        messageId, conversationId, userId, "USER", "Merhaba", Map.of(), NOW))));

    mockMvc
        .perform(
            get("/api/v1/conversations/{conversationId}/messages", conversationId)
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(messageId.toString()))
        .andExpect(jsonPath("$.content[0].role").value("USER"));
  }

  @Test
  void submitMessageReturnsAcceptedTaskReference() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    UUID agentTaskId = UUID.randomUUID();
    when(messageSubmitService.submitMessage(eq(userId), eq(conversationId), eq("idem-key"), any()))
        .thenReturn(new AgentTaskRefResponse(messageId, agentTaskId, "PENDING", NOW));

    mockMvc
        .perform(
            post("/api/v1/conversations/{conversationId}/messages", conversationId)
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString())
                .header("Idempotency-Key", "idem-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "content": "Ne alayım?",
                      "metadata": {"source": "curl"}
                    }
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.messageId").value(messageId.toString()))
        .andExpect(jsonPath("$.agentTaskId").value(agentTaskId.toString()))
        .andExpect(jsonPath("$.agentTaskStatus").value("PENDING"));
  }

  @Test
  void submitMessageMissingIdempotencyKeyReturnsBadRequest() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(messageSubmitService.submitMessage(eq(userId), eq(conversationId), eq(null), any()))
        .thenThrow(
            new ServiceException(
                ErrorCode.VALIDATION_FAILED, "Idempotency-Key header is required"));

    mockMvc
        .perform(
            post("/api/v1/conversations/{conversationId}/messages", conversationId)
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "content": "Ne alayım?"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
  }

  @Test
  void deleteConversationReturnsNoContent() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/v1/conversations/{conversationId}", conversationId)
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString()))
        .andExpect(status().isNoContent());

    verify(conversationService).deleteConversation(userId, conversationId);
  }

  @Test
  void streamConversationEventsOpensSseConnectionForOwner() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationEventStreamService.openConversationStream(userId, conversationId, null))
        .thenReturn(new SseEmitter(1_000L));

    mockMvc
        .perform(
            get("/api/v1/conversations/{conversationId}/events", conversationId)
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString()))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());

    verify(conversationEventStreamService).openConversationStream(userId, conversationId, null);
  }

  @Test
  void streamConversationEventsRejectsNonOwner() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationEventStreamService.openConversationStream(userId, conversationId, null))
        .thenThrow(new ServiceException(ErrorCode.CONVERSATION_ACCESS_DENIED));

    mockMvc
        .perform(
            get("/api/v1/conversations/{conversationId}/events", conversationId)
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("CONVERSATION_ACCESS_DENIED"));
  }

  @Test
  void getAgentTaskReturnsDto() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID messageId = UUID.randomUUID();
    UUID agentTaskId = UUID.randomUUID();
    when(conversationService.getAgentTask(userId, agentTaskId))
        .thenReturn(
            new AgentTaskResponse(
                agentTaskId,
                conversationId,
                messageId,
                userId,
                "PENDING",
                NOW,
                null,
                null,
                Map.of()));

    mockMvc
        .perform(
            get("/api/v1/agent-tasks/{agentTaskId}", agentTaskId)
                .header(AuthenticatedUserIdArgumentResolver.AUTH_USER_ID_HEADER, userId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(agentTaskId.toString()))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }
}
