package com.minimall.product.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 商品变更同步搜索索引的 MQ 配置。
 * product 服务只负责发布事件，search 服务负责声明队列并消费。
 */
@Configuration
public class ProductSearchMQConfig {

    public static final String EXCHANGE = "product.search.exchange";
    public static final String QUEUE = "product.search.queue";
    public static final String ROUTING_KEY_UPDATED = "product.updated";
    public static final String ROUTING_KEY_DELETED = "product.deleted";

    public static final String DLX = "product.search.dlx";
    public static final String DLQ = "product.search.dlq";
    public static final String DLQ_ROUTING_KEY = "product.search.dead";

    @Bean
    public DirectExchange productSearchExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue productSearchQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX);
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY);
        return new Queue(QUEUE, true, false, false, args);
    }

    @Bean
    public Binding productUpdatedBinding() {
        return BindingBuilder.bind(productSearchQueue())
                .to(productSearchExchange())
                .with(ROUTING_KEY_UPDATED);
    }

    @Bean
    public Binding productDeletedBinding() {
        return BindingBuilder.bind(productSearchQueue())
                .to(productSearchExchange())
                .with(ROUTING_KEY_DELETED);
    }

    @Bean
    public DirectExchange productSearchDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue productSearchDeadLetterQueue() {
        return new Queue(DLQ, true);
    }

    @Bean
    public Binding productSearchDeadLetterBinding() {
        return BindingBuilder.bind(productSearchDeadLetterQueue())
                .to(productSearchDeadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }
}
