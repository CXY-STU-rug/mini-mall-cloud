package com.minimall.search.listener;

import com.minimall.search.config.ProductSearchMQConfig;
import com.minimall.search.service.IProductSearchService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 商品变更事件消费者。
 *
 * product 服务只发 productId；search 服务消费后回查 product 最新数据，
 * 这样消息体轻、幂等性也更好：重复消费就是重复 upsert/delete。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchSyncListener {

    private final IProductSearchService productSearchService;

    @RabbitListener(queues = ProductSearchMQConfig.QUEUE)
    public void onProductChanged(String productIdText, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        Long productId = parseProductId(productIdText);
        if (productId == null) {
            log.warn("[product-search-mq] invalid message body={}, routingKey={}", productIdText, routingKey);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            if (ProductSearchMQConfig.ROUTING_KEY_UPDATED.equals(routingKey)) {
                productSearchService.syncById(productId);
            } else if (ProductSearchMQConfig.ROUTING_KEY_DELETED.equals(routingKey)) {
                productSearchService.deleteById(productId);
            } else {
                log.warn("[product-search-mq] unknown routingKey={} productId={}", routingKey, productId);
            }

            channel.basicAck(deliveryTag, false);
            log.info("[product-search-mq] handled routingKey={} productId={}", routingKey, productId);
        } catch (Exception e) {
            log.error("[product-search-mq] handle failed, send to DLQ routingKey={} productId={}", routingKey, productId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private Long parseProductId(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
