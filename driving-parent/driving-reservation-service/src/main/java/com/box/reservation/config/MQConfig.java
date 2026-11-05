package com.box.reservation.config;

import com.box.reservation.common.constant.RabbitMQConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class MQConfig {

    public static final String QUEUE = "reservation.success.queue";
    public static final String EXCHANGE = "reservation.success.exchange";
    public static final String ROUTING_KEY = "reservation.success.routingkey";
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
    public Exchange delayExchange(){
        return new CustomExchange(RabbitMQConstants.EXCHANGE_RESERVATION_DELAY,RabbitMQConstants.EXCHANGE_TYPE_DELAYED,true,false, Map.of(RabbitMQConstants.DELAYED_TYPE_PARAM,RabbitMQConstants.DELAYED_TYPE_DIRECT));
    }

    @Bean
    public Queue aqueue() {
        return QueueBuilder.durable(AQUEUE).build();
    }

    @Bean
    public Binding abinding() {
        return BindingBuilder.bind(aqueue()).to(delayExchange()).with(ABSENT_KEY).noargs();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
