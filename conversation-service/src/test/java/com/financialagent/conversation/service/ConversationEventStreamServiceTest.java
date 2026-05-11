package com.financialagent.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.financialagent.conversation.common.exception.ErrorCode;
import com.financialagent.conversation.common.exception.ServiceException;
import com.financialagent.conversation.config.ConversationSseProperties;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class ConversationEventStreamServiceTest {

  @Mock private ConversationAccessService conversationAccessService;

  private ConversationEventStreamRegistry streamRegistry;
  private ConversationEventStreamService streamService;

  @BeforeEach
  void setUp() {
    streamRegistry = new ConversationEventStreamRegistry();
    streamService =
        new ConversationEventStreamService(
            conversationAccessService,
            streamRegistry,
            new ConversationSseProperties(Duration.ofMinutes(5), Duration.ofSeconds(15)));
  }

  @Test
  void openConversationStreamRequiresOwnershipAndRegistersEmitter() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    SseEmitter emitter = streamService.openConversationStream(userId, conversationId);

    assertThat(emitter).isNotNull();
    verify(conversationAccessService).requireOwnedConversation(userId, conversationId);
  }

  @Test
  void openConversationStreamRejectsNonOwnerBeforeRegisteringEmitter() {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    when(conversationAccessService.requireOwnedConversation(userId, conversationId))
        .thenThrow(new ServiceException(ErrorCode.CONVERSATION_ACCESS_DENIED));

    assertThatThrownBy(() -> streamService.openConversationStream(userId, conversationId))
        .isInstanceOf(ServiceException.class)
        .extracting(exception -> ((ServiceException) exception).errorCode())
        .isEqualTo(ErrorCode.CONVERSATION_ACCESS_DENIED);
  }
}
