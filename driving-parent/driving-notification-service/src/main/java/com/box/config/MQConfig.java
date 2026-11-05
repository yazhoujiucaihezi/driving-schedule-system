package com.box.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通知服务的 MQ 拓扑。
 * <p>
 * 本服务只消费「预约成功」消息用于发通知，不参与延迟核验与缺席判定——
 * 那两个队列由 driving-reservation-service 自己声明和消费。
 * 之前这里也声明了一份 TTL 队列和死信交换机，属于重复声明：
 * 两个服务对同一个队列的参数只要有一处不同（比如 TTL 30 秒 vs 30 分钟），
 * 后启动的那个就会直接抛 PRECONDITION_FAILED 起不来。
 * </p>
 */
@Configuration
public class MQConfig {

    public static final String QUEUE = "reservation.success.queue";
    public static final String EXCHANGE = "reservation.success.exchange";
    public static final String ROUTING_KEY = "reservation.success.routingkey";

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
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
