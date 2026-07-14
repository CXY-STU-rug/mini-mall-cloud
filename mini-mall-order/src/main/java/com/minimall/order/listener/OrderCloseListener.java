package com.minimall.order.listener;

import com.minimall.order.config.RabbitMQConfig;
import com.minimall.order.service.IOrdersService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时关单消费者 (G3.7 真业务版, 从单体搬, 替代 MqTestListener.onCloseMessage)
 *
 * ════════════════════════════════════════════════════════════════
 * 触发链路 (G2 章节 48 详细讲过):
 *
 *   OrdersServiceImpl.createOrder()  →  发到 delay.exchange
 *           │                                  │
 *           │ rabbitTemplate.convertAndSend    │
 *           │      delay/<orderId>             │
 *           ▼                                  ▼
 *   ┌─ delay.queue ──┐                  TTL=30min
 *   │                │       消息在这里待 30 秒, 没消费者
 *   └────────┬───────┘
 *            │ TTL 到期 → 死信
 *            │ x-dead-letter-exchange = close.exchange
 *            │ x-dead-letter-routing-key = "close"
 *            ▼
 *   ┌─ close.queue ──┐
 *   └────────┬───────┘
 *            │ @RabbitListener 接到
 *            ▼
 *   🎧 OrderCloseListener.onOrderClose(orderId)
 *            ▼
 *   ordersService.closeOrderByMQ(orderId)
 *            ▼
 *   UPDATE orders SET status=4 WHERE id=? AND status=0   ⭐ 幂等关键
 * ════════════════════════════════════════════════════════════════
 *
 * vs 单体差异:
 *   ① 包名 com.minimall.order.listener
 *   ② 引 com.minimall.order.config.RabbitMQConfig 拿队列名常量
 *   ③ 其余 0 改动 (单体的逻辑已经很好)
 */
@Component
public class OrderCloseListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCloseListener.class);

    @Autowired
    private IOrdersService ordersService;

    /**
     * 监听 close.queue
     *
     * 方法参数 (按类型自动注入):
     *   Long orderId    — 消息体 (Producer 发的 Long, 这里接 Long, 类型必须对齐)
     *   Message message — 原始消息封装 (含 deliveryTag 等元数据)
     *   Channel channel — RabbitMQ 通道, 用来手动 ACK/NACK
     */
    @RabbitListener(queues = RabbitMQConfig.CLOSE_QUEUE)
    public void onOrderClose(Long orderId, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            log.info("[MQ-CLOSE] 收到关单消息 orderId={}, deliveryTag={}", orderId, deliveryTag);

            // ⭐ 调真业务 (closeOrderByMQ 自带幂等: 只关 status=0 的)
            ordersService.closeOrderByMQ(orderId);

            log.info("[MQ-CLOSE] 关单处理完成 orderId={}", orderId);

            // ⭐ ACK: 消息成功消费, 从队列删除
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            // 业务异常时:
            //   - NACK 不 requeue → 消息丢弃 (生产推荐再配独立死信队列接收)
            //   - 这里偷懒丢弃, 教学场景可接受
            log.error("[MQ-CLOSE] 关单失败 orderId={}", orderId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
