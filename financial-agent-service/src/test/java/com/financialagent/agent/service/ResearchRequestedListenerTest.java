package com.financialagent.agent.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class ResearchRequestedListenerTest {

  @Mock private ResearchRequestProcessor processor;
  @Mock private Channel channel;

  private ResearchRequestedListener listener;

  @BeforeEach
  void setUp() {
    listener = new ResearchRequestedListener(processor);
  }

  @Test
  void validRequestIsProcessedAndAcked() throws Exception {
    Map<String, Object> payload = payload();
    Message message = message(42L);
    when(processor.process(org.mockito.ArgumentMatchers.any()))
        .thenReturn(ResearchRequestProcessor.ProcessingResult.PROCESSED);

    listener.onMessage(payload, message, channel);

    verify(channel).basicAck(42L, false);
  }

  @Test
  void processingFailureIsNackedWithoutRequeueForDlq() throws Exception {
    Map<String, Object> payload = payload();
    Message message = message(43L);
    when(processor.process(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("boom"));

    listener.onMessage(payload, message, channel);

    verify(channel).basicNack(43L, false, false);
  }

  private Message message(long deliveryTag) {
    MessageProperties properties = new MessageProperties();
    properties.setDeliveryTag(deliveryTag);
    return new Message(new byte[0], properties);
  }

  private Map<String, Object> payload() {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventId", UUID.randomUUID().toString());
    payload.put("schemaVersion", "1.0");
    payload.put("correlationId", UUID.randomUUID().toString());
    payload.put("idempotencyKey", "idempotency-key");
    payload.put("userId", UUID.randomUUID().toString());
    payload.put("conversationId", UUID.randomUUID().toString());
    payload.put("messageId", UUID.randomUUID().toString());
    payload.put("agentTaskId", UUID.randomUUID().toString());
    payload.put("userMessage", "Ne alayım?");
    payload.put("occurredAt", Instant.parse("2026-05-11T10:00:00Z").toString());
    payload.put("intentHint", "BIST_STOCK");
    return payload;
  }
}
