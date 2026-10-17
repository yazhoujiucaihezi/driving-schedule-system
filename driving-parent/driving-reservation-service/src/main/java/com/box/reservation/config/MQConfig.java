package com.box.reservation.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MQConfig {

    public static final String QUEUE = "reservation.success.queue";
    public static final String EXCHANGE = "reservation.success.exchange";
    public static final String ROUTING_KEY = "reservation.success.routingkey";
    public static final String DQUEUE = "reservation.delay.queue";
    public static final String DEXCHANGE = "reservation.dlx.exchange";
    public static final String AQUEUE = "reservation.absent.queue";
    public static final String ABSENT_KEY = "reservation.absent.routingkey";

    @Bean
    public Exchange exchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).with(ROUTING_KEY).noargs();
    }

    @Bean
    public Queue dqueue() {
        return QueueBuilder
                .durable(DQUEUE)
                .ttl(300000)
                .deadLetterExchange(DEXCHANGE)
                .deadLetterRoutingKey(ABSENT_KEY)
                .build();
    }

    @Bean
    public Exchange dexchange() {
        return ExchangeBuilder.topicExchange(DEXCHANGE).durable(true).build();
    }
    @Bean
    public Binding abinding() {
        return BindingBuilder
                .bind(aqueue())
                .to(dexchange())
                .with(ABSENT_KEY)
                .noargs();
    }
    @Bean
    public Queue aqueue() {
        return QueueBuilder.durable(AQUEUE).build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
