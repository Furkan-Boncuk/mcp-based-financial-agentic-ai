package com.financialagent.agent.service;

import com.financialagent.agent.dto.ResearchRequestedEvent;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ResearchRequestedListener {

  private static final Logger log = LoggerFactory.getLogger(ResearchRequestedListener.class);

  private final ResearchRequestProcessor processor;

  public ResearchRequestedListener(ResearchRequestProcessor processor) {
    this.processor = processor;
  }

  @RabbitListener(
      queues = "${agent.rabbitmq.queues.task}",
      containerFactory = "rabbitListenerContainerFactory")
  public void onMessage(Map<String, Object> payload, Message message, Channel channel)
      throws IOException {
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    try {
      ResearchRequestProcessor.ProcessingResult result =
          processor.process(ResearchRequestedEvent.from(payload));
      if (result == ResearchRequestProcessor.ProcessingResult.DUPLICATE) {
        log.debug("Duplicate ResearchRequested event skipped");
      }
      channel.basicAck(deliveryTag, false);
    } catch (Exception exception) {
      log.warn(
          "ResearchRequested processing failed, dead-lettering message errorType={}",
          exception.getClass().getSimpleName());
      channel.basicNack(deliveryTag, false, false);
    }
  }
}
