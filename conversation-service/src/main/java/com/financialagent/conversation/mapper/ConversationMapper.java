package com.financialagent.conversation.mapper;

import com.financialagent.conversation.domain.AgentTask;
import com.financialagent.conversation.domain.Conversation;
import com.financialagent.conversation.domain.Message;
import com.financialagent.conversation.dto.AgentTaskRefResponse;
import com.financialagent.conversation.dto.AgentTaskResponse;
import com.financialagent.conversation.dto.ConversationResponse;
import com.financialagent.conversation.dto.MessageResponse;
import org.springframework.stereotype.Component;

@Component
public class ConversationMapper {

  public ConversationResponse toConversationResponse(Conversation conversation) {
    return new ConversationResponse(
        conversation.id(),
        conversation.title(),
        conversation.description(),
        conversation.userId(),
        conversation.createdAt(),
        conversation.updatedAt());
  }

  public MessageResponse toMessageResponse(Message message) {
    return new MessageResponse(
        message.id(),
        message.conversationId(),
        message.userId(),
        message.role().name(),
        message.content(),
        message.metadata(),
        message.createdAt());
  }

  public AgentTaskRefResponse toAgentTaskRefResponse(AgentTask agentTask) {
    return new AgentTaskRefResponse(
        agentTask.messageId(), agentTask.id(), agentTask.status().name(), agentTask.createdAt());
  }

  public AgentTaskResponse toAgentTaskResponse(AgentTask agentTask) {
    return new AgentTaskResponse(
        agentTask.id(),
        agentTask.conversationId(),
        agentTask.messageId(),
        agentTask.userId(),
        agentTask.status().name(),
        agentTask.createdAt(),
        agentTask.startedAt(),
        agentTask.completedAt(),
        agentTask.resultMetadata());
  }
}
