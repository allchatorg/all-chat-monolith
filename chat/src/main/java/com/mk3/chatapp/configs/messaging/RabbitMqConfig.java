package com.mk3.chatapp.configs.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnableRabbit
@Configuration
@EnableConfigurationProperties(CsamMessagingProperties.class)
@ConditionalOnProperty(prefix = "csam.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMqConfig {

    @Bean
    public Declarables csamMessagingDeclarables(CsamMessagingProperties properties) {
        DirectExchange csamExchange = new DirectExchange(properties.exchange(), true, false);
        Queue analysisRequestQueue = new Queue(properties.analysisRequestQueue(), true);
        Queue analysisResultQueue = new Queue(properties.analysisResultQueue(), true);
        Binding analysisRequestBinding = BindingBuilder.bind(analysisRequestQueue)
                .to(csamExchange)
                .with(properties.analysisRequestRoutingKey());
        Binding analysisResultBinding = BindingBuilder.bind(analysisResultQueue)
                .to(csamExchange)
                .with(properties.analysisResultRoutingKey());

        return new Declarables(
                csamExchange,
                analysisRequestQueue,
                analysisResultQueue,
                analysisRequestBinding,
                analysisResultBinding);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
