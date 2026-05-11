package com.financialagent.conversation.controller;

import com.financialagent.conversation.common.web.AuthenticatedUserId;
import com.financialagent.conversation.dto.AgentTaskRefResponse;
import com.financialagent.conversation.dto.AgentTaskResponse;
import com.financialagent.conversation.dto.ConversationCreateRequest;
import com.financialagent.conversation.dto.ConversationResponse;
import com.financialagent.conversation.dto.MessageCreateRequest;
import com.financialagent.conversation.dto.MessageResponse;
import com.financialagent.conversation.dto.PageResponse;
import com.financialagent.conversation.service.ConversationService;
import com.financialagent.conversation.service.MessageSubmitService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ConversationController {

  private final ConversationService conversationService;
  private final MessageSubmitService messageSubmitService;

  public ConversationController(
      ConversationService conversationService, MessageSubmitService messageSubmitService) {
    this.conversationService = conversationService;
    this.messageSubmitService = messageSubmitService;
  }

  @PostMapping("/conversations")
  public ResponseEntity<ConversationResponse> createConversation(
      @AuthenticatedUserId UUID userId, @Valid @RequestBody ConversationCreateRequest request) {
    ConversationResponse response = conversationService.createConversation(userId, request);
    return ResponseEntity.created(URI.create("/api/v1/conversations/" + response.id()))
        .body(response);
  }

  @GetMapping("/conversations")
  public PageResponse<ConversationResponse> listConversations(
      @AuthenticatedUserId UUID userId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return PageResponse.from(conversationService.listConversations(userId, pageable));
  }

  @GetMapping("/conversations/{conversationId}")
  public ConversationResponse getConversation(
      @AuthenticatedUserId UUID userId, @PathVariable("conversationId") UUID conversationId) {
    return conversationService.getConversation(userId, conversationId);
  }

  @GetMapping("/conversations/{conversationId}/messages")
  public PageResponse<MessageResponse> listMessages(
      @AuthenticatedUserId UUID userId,
      @PathVariable("conversationId") UUID conversationId,
      @PageableDefault(size = 50) Pageable pageable) {
    return PageResponse.from(conversationService.listMessages(userId, conversationId, pageable));
  }

  @PostMapping("/conversations/{conversationId}/messages")
  public ResponseEntity<AgentTaskRefResponse> submitMessage(
      @AuthenticatedUserId UUID userId,
      @PathVariable("conversationId") UUID conversationId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody MessageCreateRequest request) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(messageSubmitService.submitMessage(userId, conversationId, idempotencyKey, request));
  }

  @DeleteMapping("/conversations/{conversationId}")
  public ResponseEntity<Void> deleteConversation(
      @AuthenticatedUserId UUID userId, @PathVariable("conversationId") UUID conversationId) {
    conversationService.deleteConversation(userId, conversationId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/agent-tasks/{agentTaskId}")
  public AgentTaskResponse getAgentTask(
      @AuthenticatedUserId UUID userId, @PathVariable("agentTaskId") UUID agentTaskId) {
    return conversationService.getAgentTask(userId, agentTaskId);
  }
}
