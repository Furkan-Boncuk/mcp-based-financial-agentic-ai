package com.financialagent.conversation.service;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class AgentResultEventListener {

  private static final Logger log = LoggerFactory.getLogger(AgentResultEventListener.class);

  private final AgentResultEventService eventService;

  public AgentResultEventListener(AgentResultEventService eventService) {
    this.eventService = eventService;
  }

  @RabbitListener(
      queues = "${agent.rabbitmq.queues.result}",
      containerFactory = "rabbitListenerContainerFactory")
  public void onMessage(
      @Payload Map<String, Object> payload,
      @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
      Message message,
      Channel channel)
      throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    try {
      eventService.handle(routingKey, payload);
      channel.basicAck(deliveryTag, false);
    } catch (RuntimeException exception) {
      log.warn("Agent result event processing failed for routingKey={}", routingKey, exception);
      channel.basicNack(deliveryTag, false, false);
    }
  }
}
