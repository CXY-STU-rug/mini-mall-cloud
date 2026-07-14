package com.minimall.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置 (G2 - 从单体 com.minimall.minimall.config 搬过来)
 *
 * ════════════════════════════════════════════════════════════════
 * 这个文件干两件事:
 *   ① 用 @Bean 声明出 3 组 [Exchange + Queue + Binding] (共 9 个 Bean)
 *   ② Spring 启动后, RabbitAdmin 自动把这些 Bean 同步到 Broker
 *      → 启动完去 http://localhost:15672 看, 队列/交换机就有了
 *
 * 3 组队列的用途:
 *   1) order.delay.exchange / delay.queue
 *        延迟队列, 30 秒 TTL + DLX → close.exchange
 *        给 OrdersServiceImpl 创建订单时用 (G3.7 才搬)
 *
 *   2) order.close.exchange / close.queue
 *        死信交换机的【出口】, 由 OrderCloseListener 消费, 关订单
 *        (G3.7 才搬 Listener)
 *
 *   3) seckill.exchange / seckill.queue
 *        秒杀异步下单 (G3.8 才搬)
 *
 * G2 阶段先把【队列基建】搭好, 业务 Producer/Consumer 后续阶段陆续搬
 * ════════════════════════════════════════════════════════════════
 */
@Configuration
public class RabbitMQConfig {

    // ━━━━━━━━━━━━━━━ 常量: 名字统一管理 ━━━━━━━━━━━━━━━
    // 提到出常量, 全项目引用方便, 后续改名一个地方搞定
    // public 是因为 Listener 用 @RabbitListener(queues = RabbitMQConfig.CLOSE_QUEUE)

    public static final String DELAY_EXCHANGE      = "order.delay.exchange";
    public static final String DELAY_QUEUE         = "order.delay.queue";
    public static final String DELAY_ROUTING_KEY   = "delay";

    public static final String CLOSE_EXCHANGE      = "order.close.exchange";
    public static final String CLOSE_QUEUE         = "order.close.queue";
    public static final String CLOSE_ROUTING_KEY   = "close";

    public static final String SECKILL_EXCHANGE    = "seckill.exchange";
    public static final String SECKILL_QUEUE       = "seckill.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill";

    // ━━━━━━━━━━━━━━━ 组 1: 延迟队列 (TTL + DLX) ━━━━━━━━━━━━━━━

    /**
     * 延迟交换机
     *
     * DirectExchange 构造: name, durable, autoDelete
     *   durable=true:   持久化, RabbitMQ 重启后这个 Exchange 还在
     *   autoDelete=false: 即使没 Queue 绑定它, 也不自动删
     */
    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE, true, false);
    }

    /**
     * 延迟队列 - 整个 RabbitMQ 配置的【骚操作核心】
     *
     * 这个队列【没人消费】, 消息进来就死等 30 秒, 然后被 RabbitMQ 自动:
     *   ① 标记为"死信" (TTL 到期是死信的一种, 还有"NACK 不 requeue"和"队列满")
     *   ② 转发到 x-dead-letter-exchange 指定的交换机
     *   ③ 用 x-dead-letter-routing-key 指定的 routingKey 路由
     *
     * 用 args (Map<String, Object>) 配置这两个特殊属性:
     *   x-message-ttl:               消息存活时间 (毫秒), 这里 30000 = 30 秒
     *   x-dead-letter-exchange:      死信去哪个 Exchange (这里是关单交换机)
     *   x-dead-letter-routing-key:   死信用什么 routingKey (一般跟下游 binding 的 routing key 对上)
     */
    @Bean
    public Queue delayQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 1800000);                      // ⏱ 30 分钟 (原 30000=30秒 太短, 真实支付来不及, 会出现"付款成功但订单已超时关单"的资损)
        args.put("x-dead-letter-exchange", CLOSE_EXCHANGE);      // 💀 死信转发到关单交换机
        args.put("x-dead-letter-routing-key", CLOSE_ROUTING_KEY);// 💀 死信带的 routingKey

        // Queue 构造: name, durable, exclusive, autoDelete, args
        //   exclusive=false:  不独占, 多个连接都能用
        //   autoDelete=false: 即使没消费者, 也不自动删 (我们就是要等 TTL)
        return new Queue(DELAY_QUEUE, true, false, false, args);
    }

    /**
     * 延迟队列绑定到延迟交换机
     *
     * BindingBuilder 是流式 API, 写法非常自然:
     *   bind(队列).to(交换机).with(routingKey)
     *
     * 翻译: 把 delayQueue 绑到 delayExchange, 接收 routingKey="delay" 的消息
     */
    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue())
                .to(delayExchange())
                .with(DELAY_ROUTING_KEY);
    }

    // ━━━━━━━━━━━━━━━ 组 2: 关单队列 (死信接收方) ━━━━━━━━━━━━━━━

    @Bean
    public DirectExchange closeExchange() {
        return new DirectExchange(CLOSE_EXCHANGE, true, false);
    }

    /**
     * 关单队列 - 普通队列 (无 TTL/DLX 配置)
     *
     * Queue(name, durable) 是简化构造, 等价于 (name, true, false, false)
     */
    @Bean
    public Queue closeQueue() {
        return new Queue(CLOSE_QUEUE, true);
    }

    @Bean
    public Binding closeBinding() {
        return BindingBuilder.bind(closeQueue())
                .to(closeExchange())
                .with(CLOSE_ROUTING_KEY);
    }

    // ━━━━━━━━━━━━━━━ 组 3: 秒杀异步下单队列 ━━━━━━━━━━━━━━━

    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillQueue() {
        return new Queue(SECKILL_QUEUE, true);
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }
}
