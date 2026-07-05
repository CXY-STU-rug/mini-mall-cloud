package com.minimall.order.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.order.client.ProductFeignClient;
import com.minimall.order.config.RabbitMQConfig;
import com.minimall.order.constant.OrderStatus;
import com.minimall.order.dto.SeckillOrderMessage;
import com.minimall.order.entity.OrderItem;
import com.minimall.order.entity.Orders;
import com.minimall.order.entity.SeckillActivity;
import com.minimall.order.entity.SeckillOrder;
import com.minimall.order.mapper.OrderItemMapper;
import com.minimall.order.mapper.OrdersMapper;
import com.minimall.order.mapper.SeckillOrderMapper;
import com.minimall.order.service.ISeckillActivityService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;

/**
 * 秒杀订单 MQ 消费者。
 *
 * 消费成功后不再只写 seckill_order，而是创建一张真正的普通订单：
 * orders + order_item + seckill_order 追踪记录。
 */
@Component
public class SeckillOrderListener {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderListener.class);

    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductFeignClient productFeignClient;
    @Autowired private ISeckillActivityService seckillActivityService;
    @Autowired private OrdersMapper ordersMapper;
    @Autowired private OrderItemMapper orderItemMapper;
    @Autowired private SeckillOrderMapper seckillOrderMapper;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void onSeckillMessage(String msg, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("[MQ-Seckill] 收到秒杀消息 msg={}", msg);

        SeckillOrderMessage payload = null;
        boolean orderCreated = false;

        try {
            payload = parseMessage(msg);
            if (payload == null) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            // lambda 只能捕获 effectively final 变量; payload 在上面被重新赋值过(63行null→67行赋值),
            // 不是 final, 直接进 lambda 会编译报错。复制成一个不再改动的 final 副本给 lambda 用。
            final SeckillOrderMessage finalPayload = payload;
            Long orderId = transactionTemplate.execute(status -> createNormalOrder(finalPayload));
            orderCreated = orderId != null;

            if (orderId != null) {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.DELAY_EXCHANGE,
                        RabbitMQConfig.DELAY_ROUTING_KEY,
                        orderId
                );
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[MQ-Seckill] 处理失败 msg={}", msg, e);
            if (!orderCreated && payload != null) {
                rollbackSeckillQuota(payload.getActivityId(), payload.getUserId());
            }
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private SeckillOrderMessage parseMessage(String msg) throws Exception {
        if (msg == null || msg.trim().isEmpty()) {
            return null;
        }

        String text = msg.trim();
        if (!text.startsWith("{")) {
            log.warn("[MQ-Seckill] 跳过旧格式消息，旧消息缺少地址快照 msg={}", msg);
            return null;
        }
        return objectMapper.readValue(text, SeckillOrderMessage.class);
    }

    private Long createNormalOrder(SeckillOrderMessage payload) {
        Long activityId = payload.getActivityId();
        Long userId = payload.getUserId();

        QueryWrapper<SeckillOrder> existsWrapper = new QueryWrapper<>();
        existsWrapper.eq("user_id", userId).eq("seckill_activity_id", activityId);
        Long exists = seckillOrderMapper.selectCount(existsWrapper);
        if (exists != null && exists > 0) {
            log.warn("[MQ-Seckill] 重复消息跳过 userId={} activityId={}", userId, activityId);
            return null;
        }

        SeckillActivity activity = seckillActivityService.getById(activityId);
        if (activity == null) {
            throw new BusinessException(404, "秒杀活动不存在");
        }

        Result<Map<String, Object>> productResp = productFeignClient.getById(activity.getProductId());
        if (productResp == null || productResp.getCode() != 200 || productResp.getData() == null) {
            throw new BusinessException(400, "商品不存在");
        }
        Map<String, Object> product = productResp.getData();

        boolean productStockDeducted = false;
        try {
            Result<Integer> deductResp = productFeignClient.deductStock(activity.getProductId(), 1);
            if (deductResp == null || deductResp.getCode() != 200) {
                throw new BusinessException(400, "商品库存不足");
            }
            productStockDeducted = true;

            LocalDateTime now = LocalDateTime.now();
            BigDecimal seckillPrice = activity.getSeckillPrice();
            String orderNo = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + userId + String.format("%04d", new Random().nextInt(10000));

            Orders order = new Orders();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setTotalAmount(seckillPrice);
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setStatus(OrderStatus.UNPAID);
            order.setReceiver(payload.getReceiver());
            order.setPhone(payload.getPhone());
            order.setAddress(payload.getAddress());
            order.setRemark("秒杀活动 #" + activityId);
            order.setCreateTime(now);
            order.setUpdateTime(now);
            order.setIsDeleted((byte) 0);
            ordersMapper.insert(order);

            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setProductId(activity.getProductId());
            item.setProductName(text(product.get("name")));
            item.setProductImage(text(product.get("coverImage")));
            item.setPrice(seckillPrice);
            item.setQuantity(1);
            item.setSubtotal(seckillPrice);
            item.setCreateTime(now);
            orderItemMapper.insert(item);

            SeckillOrder seckillOrder = new SeckillOrder();
            seckillOrder.setOrderNo(orderNo);
            seckillOrder.setUserId(userId);
            seckillOrder.setSeckillActivityId(activityId);
            seckillOrder.setProductId(activity.getProductId());
            seckillOrder.setSeckillPrice(seckillPrice);
            seckillOrder.setStatus(OrderStatus.UNPAID);
            seckillOrder.setPayTime(null);
            seckillOrder.setCreateTime(now);
            seckillOrder.setUpdateTime(now);
            seckillOrder.setIsDeleted(0);
            seckillOrderMapper.insert(seckillOrder);

            log.info("[MQ-Seckill] 普通订单已生成 orderId={} orderNo={} userId={}", order.getId(), orderNo, userId);
            return order.getId();
        } catch (RuntimeException e) {
            if (productStockDeducted) {
                try {
                    productFeignClient.restoreStock(activity.getProductId(), 1);
                } catch (Exception restoreEx) {
                    log.error("[MQ-Seckill] 创建订单失败后回补商品库存也失败 productId={}", activity.getProductId(), restoreEx);
                }
            }
            throw e;
        }
    }

    private void rollbackSeckillQuota(Long activityId, Long userId) {
        if (activityId == null || userId == null) {
            return;
        }
        stringRedisTemplate.opsForValue().increment("seckill:stock:" + activityId);
        stringRedisTemplate.opsForSet().remove("seckill:bought:" + activityId, userId.toString());
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
