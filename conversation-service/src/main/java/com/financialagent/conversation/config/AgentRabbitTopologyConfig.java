package com.financialagent.conversation.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AgentRabbitTopologyProperties.class)
public class AgentRabbitTopologyConfig {

  private final AgentRabbitTopologyProperties properties;

  public AgentRabbitTopologyConfig(AgentRabbitTopologyProperties properties) {
    this.properties = properties;
  }

  @Bean
  public Declarables agentRabbitDeclarables() {
    TopicExchange topicExchange =
        ExchangeBuilder.topicExchange(properties.exchanges().topic()).durable(true).build();
    TopicExchange retryExchange =
        ExchangeBuilder.topicExchange(properties.exchanges().retry()).durable(true).build();
    DirectExchange dlqExchange =
        ExchangeBuilder.directExchange(properties.exchanges().dlq()).durable(true).build();

    Queue taskQueue =
        QueueBuilder.durable(properties.queues().task())
            .ttl(toMillis(properties.taskQueueTtl()))
            .maxLength(properties.taskQueueMaxLength())
            .deadLetterExchange(properties.exchanges().dlq())
            .deadLetterRoutingKey(properties.routingKeys().researchDeadlettered())
            .build();
    Queue resultQueue =
        QueueBuilder.durable(properties.queues().result())
            .ttl(toMillis(properties.resultQueueTtl()))
            .build();
    Queue retry1sQueue =
        retryQueue(properties.queues().retry1s(), toMillis(properties.retryDelays().retry1s()));
    Queue retry5sQueue =
        retryQueue(properties.queues().retry5s(), toMillis(properties.retryDelays().retry5s()));
    Queue retry15sQueue =
        retryQueue(properties.queues().retry15s(), toMillis(properties.retryDelays().retry15s()));
    Queue dlq =
        QueueBuilder.durable(properties.queues().dlq()).ttl(toMillis(properties.dlqTtl())).build();

    return new Declarables(
        topicExchange,
        retryExchange,
        dlqExchange,
        taskQueue,
        resultQueue,
        retry1sQueue,
        retry5sQueue,
        retry15sQueue,
        dlq,
        bind(taskQueue, topicExchange, properties.routingKeys().researchRequested()),
        bind(resultQueue, topicExchange, properties.routingKeys().resultPattern()),
        bind(retry1sQueue, retryExchange, properties.routingKeys().retry1s()),
        bind(retry5sQueue, retryExchange, properties.routingKeys().retry5s()),
        bind(retry15sQueue, retryExchange, properties.routingKeys().retry15s()),
        BindingBuilder.bind(dlq)
            .to(dlqExchange)
            .with(properties.routingKeys().researchDeadlettered()));
  }

  @Bean
  public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
    RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
    rabbitAdmin.setAutoStartup(true);
    return rabbitAdmin;
  }

  @Bean
  public RabbitListenerContainerFactory<SimpleMessageListenerContainer>
      rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
    factory.setPrefetchCount(1);
    factory.setConcurrentConsumers(3);
    return factory;
  }

  @Bean
  public ApplicationRunner agentRabbitTopologyInitializer(RabbitAdmin rabbitAdmin) {
    return args -> rabbitAdmin.initialize();
  }

  private Queue retryQueue(String queueName, int delayMs) {
    return QueueBuilder.durable(queueName)
        .ttl(delayMs)
        .deadLetterExchange(properties.exchanges().topic())
        .deadLetterRoutingKey(properties.routingKeys().researchRequested())
        .build();
  }

  private Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
    return BindingBuilder.bind(queue).to(exchange).with(routingKey);
  }

  private int toMillis(java.time.Duration duration) {
    return Math.toIntExact(duration.toMillis());
  }
}
