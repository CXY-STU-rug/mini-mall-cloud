package com.minimall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.order.client.ProductFeignClient;
import com.minimall.order.client.UserFeignClient;
import com.minimall.order.config.RabbitMQConfig;
import com.minimall.order.dto.SeckillActivityDTO;
import com.minimall.order.dto.SeckillOrderMessage;
import com.minimall.order.entity.Orders;
import com.minimall.order.entity.SeckillActivity;
import com.minimall.order.entity.SeckillOrder;
import com.minimall.order.mapper.OrdersMapper;
import com.minimall.order.mapper.SeckillActivityMapper;
import com.minimall.order.mapper.SeckillOrderMapper;
import com.minimall.order.service.ISeckillActivityService;
import com.minimall.order.vo.SeckillActivityVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀活动服务。
 *
 * 新链路：Redis Lua 抢资格 -> MQ 异步创建普通订单 -> 复用普通订单支付。
 */
@Service
public class SeckillActivityServiceImpl
        extends ServiceImpl<SeckillActivityMapper, SeckillActivity>
        implements ISeckillActivityService {

    @Autowired private ProductFeignClient productFeignClient;
    @Autowired private UserFeignClient userFeignClient;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private DefaultRedisScript<Long> seckillStockScript;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SeckillOrderMapper seckillOrderMapper;
    @Autowired private OrdersMapper ordersMapper;

    @Override
    public Long publishActivity(SeckillActivityDTO dto) {
        if (dto.getProductId() == null || dto.getProductId() <= 0) {
            throw new BusinessException(400, "商品 ID 必填");
        }
        if (dto.getSeckillPrice() == null || dto.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "秒杀价必须大于 0");
        }
        if (dto.getStock() == null || dto.getStock() <= 0) {
            throw new BusinessException(400, "秒杀库存必须大于 0");
        }
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new BusinessException(400, "活动时间必填");
        }
        if (dto.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "开始时间不能早于现在");
        }
        if (dto.getEndTime().isBefore(dto.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }

        Result<Map<String, Object>> resp = productFeignClient.getById(dto.getProductId());
        if (resp == null || resp.getCode() != 200 || resp.getData() == null) {
            throw new BusinessException(400, "商品不存在");
        }
        Map<String, Object> product = resp.getData();
        if (Integer.parseInt(product.get("status").toString()) == 0) {
            throw new BusinessException(400, "商品已下架");
        }

        BigDecimal originalPrice = new BigDecimal(product.get("price").toString());
        if (dto.getSeckillPrice().compareTo(originalPrice) >= 0) {
            throw new BusinessException(400, "秒杀价必须低于商品原价");
        }

        Integer productStock = Integer.parseInt(product.get("stock").toString());
        if (productStock < dto.getStock()) {
            throw new BusinessException(400, "商品库存不足以支撑秒杀");
        }

        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(dto.getProductId());
        activity.setSeckillPrice(dto.getSeckillPrice());
        activity.setStock(dto.getStock());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setStatus((byte) 0);
        this.save(activity);
        return activity.getId();
    }

    @Override
    public List<SeckillActivityVO> listActiveActivities() {
        QueryWrapper<SeckillActivity> wrapper = new QueryWrapper<>();
        wrapper.ne("status", 2).orderByAsc("start_time");
        List<SeckillActivity> activities = this.list(wrapper);
        if (activities.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, Map<String, Object>> productMap = new HashMap<>();
        for (SeckillActivity activity : activities) {
            Result<Map<String, Object>> resp = productFeignClient.getById(activity.getProductId());
            if (resp != null && resp.getCode() == 200 && resp.getData() != null) {
                productMap.put(activity.getProductId(), resp.getData());
            }
        }

        List<SeckillActivityVO> result = new ArrayList<>();
        for (SeckillActivity activity : activities) {
            Map<String, Object> product = productMap.get(activity.getProductId());

            SeckillActivityVO vo = new SeckillActivityVO();
            vo.setId(activity.getId());
            vo.setProductId(activity.getProductId());
            vo.setProductName(product == null ? null : text(product.get("name")));
            vo.setProductImage(product == null ? null : text(product.get("coverImage")));
            vo.setOriginalPrice(product == null ? null : new BigDecimal(product.get("price").toString()));
            vo.setSeckillPrice(activity.getSeckillPrice());
            vo.setStock(activity.getStock());
            vo.setStartTime(activity.getStartTime());
            vo.setEndTime(activity.getEndTime());
            vo.setStatus(activity.getStatus());
            vo.setStatusDesc(statusDesc(activity.getStatus()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public String seckill(Long userId, Long activityId, Long addressId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (addressId == null) {
            throw new BusinessException(400, "请先选择收货地址");
        }

        SeckillActivity activity = this.getById(activityId);
        if (activity == null) {
            throw new BusinessException(404, "活动不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException(400, "活动还未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException(400, "活动已结束");
        }

        Map<String, Object> address = loadAddressSnapshot(addressId);

        String stockKey = "seckill:stock:" + activityId;
        String boughtKey = "seckill:bought:" + activityId;
        if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(stockKey))) {
            stringRedisTemplate.opsForValue().set(stockKey, activity.getStock().toString());
        }

        Long result = stringRedisTemplate.execute(
                seckillStockScript,
                Arrays.asList(stockKey, boughtKey),
                userId.toString()
        );

        if (result == null) {
            throw new BusinessException(500, "系统繁忙，请稍后再试");
        }
        if (result == -2L) {
            throw new BusinessException(400, "活动未开始或未预热");
        }
        if (result == -1L) {
            throw new BusinessException(400, "您已经参与过本次秒杀");
        }
        if (result == 0L) {
            throw new BusinessException(400, "已售罄");
        }

        if (result == 1L) {
            SeckillOrderMessage message = new SeckillOrderMessage();
            message.setActivityId(activityId);
            message.setUserId(userId);
            message.setAddressId(addressId);
            message.setReceiver(text(address.get("receiver")));
            message.setPhone(text(address.get("phone")));
            message.setAddress(text(address.get("province"))
                    + text(address.get("city"))
                    + text(address.get("district"))
                    + text(address.get("detail")));

            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.SECKILL_EXCHANGE,
                        RabbitMQConfig.SECKILL_ROUTING_KEY,
                        objectMapper.writeValueAsString(message)
                );
            } catch (JsonProcessingException e) {
                rollbackSeckillQuota(activityId, userId);
                throw new BusinessException(500, "订单排队失败，请稍后再试");
            }
            return "抢购成功，订单正在生成，请稍后查看结果";
        }

        throw new BusinessException(500, "未知错误");
    }

    @Override
    public Map<String, Object> querySeckillResult(Long userId, Long activityId) {
        Map<String, Object> result = new HashMap<>();

        QueryWrapper<SeckillOrder> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("seckill_activity_id", activityId);
        SeckillOrder seckillOrder = seckillOrderMapper.selectOne(wrapper);
        if (seckillOrder != null) {
            Orders normalOrder = ordersMapper.selectOne(
                    new QueryWrapper<Orders>()
                            .eq("order_no", seckillOrder.getOrderNo())
                            .last("limit 1")
            );

            result.put("status", "SUCCESS");
            result.put("orderNo", seckillOrder.getOrderNo());
            result.put("orderId", normalOrder == null ? null : normalOrder.getId());
            result.put("message", normalOrder == null ? "已抢到资格，订单同步中" : "订单已生成，请尽快支付");
            return result;
        }

        String boughtKey = "seckill:bought:" + activityId;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(boughtKey, userId.toString());
        if (Boolean.TRUE.equals(isMember)) {
            result.put("status", "PROCESSING");
            result.put("orderNo", null);
            result.put("orderId", null);
            result.put("message", "订单生成中，请稍后再查");
            return result;
        }

        result.put("status", "NOT_FOUND");
        result.put("orderNo", null);
        result.put("orderId", null);
        result.put("message", "未抢到，请下次再来");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySeckillOrder(Long userId, String orderNo) {
        throw new BusinessException(400, "秒杀订单已改为普通订单支付，请前往订单支付页完成支付");
    }

    private Map<String, Object> loadAddressSnapshot(Long addressId) {
        Result<Map<String, Object>> addrResp = userFeignClient.getAddress(addressId);
        if (addrResp == null || addrResp.getCode() != 200 || addrResp.getData() == null) {
            throw new BusinessException(403, "收货地址无效");
        }
        return addrResp.getData();
    }

    private void rollbackSeckillQuota(Long activityId, Long userId) {
        stringRedisTemplate.opsForValue().increment("seckill:stock:" + activityId);
        stringRedisTemplate.opsForSet().remove("seckill:bought:" + activityId, userId.toString());
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String statusDesc(Byte status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0:
                return "待开始";
            case 1:
                return "进行中";
            case 2:
                return "已结束";
            default:
                return "未知";
        }
    }
}
