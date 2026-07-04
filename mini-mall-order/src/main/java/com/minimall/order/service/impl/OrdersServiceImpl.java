package com.minimall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;   // SEC-2: 条件 UPDATE(原子状态机)用
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.order.client.ProductFeignClient;
import com.minimall.order.client.UserFeignClient;
import com.minimall.order.config.RabbitMQConfig;
import com.minimall.order.constant.OrderStatus;
import com.minimall.order.dto.CreateOrderDTO;
import com.minimall.order.dto.ShipOrderDTO;
import com.minimall.order.dto.UseCouponDTO;
import com.minimall.order.entity.CartItem;
import com.minimall.order.entity.OrderItem;
import com.minimall.order.entity.Orders;
import com.minimall.order.mapper.OrderItemMapper;
import com.minimall.order.mapper.OrdersMapper;
import com.minimall.order.service.ICartItemService;
import com.minimall.order.service.IOrderItemService;
import com.minimall.order.service.IOrdersService;
import com.minimall.order.util.RedisLockUtil;
import com.minimall.order.vo.OrderDetailVO;
import com.minimall.order.vo.OrderItemVO;
import com.minimall.order.vo.OrderListVO;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 订单服务实现 (G3.7 搬迁 + G3.10 补扣库存 + H1 Feign fallback)
 *
 * ════════════════════════════════════════════════════════════════
 * vs 单体差异:
 *   ① userId 不再读 UserContext, 从【方法参数】传入
 *   ② addressService.getById  → userFeignClient.getAddress  (HTTP)
 *   ③ productService.listByIds → 循环 productFeignClient.getById (HTTP × N)
 *      性能差但教学简单, 生产应给 product 加 batch 接口
 *   ④ 扣/回库存改成 Feign 跨服务调用 productFeignClient.deductStock / restoreStock
 *      ⚠ 跨服务事务局限: order 抛异常时 product 已扣的库存不会自动回滚
 *        (分布式事务问题, 等 Seata/MQ 补偿表解)
 *   ⑤ 商品/地址数据 Map<String, Object>, 因为 order 引不到 Product/Address entity
 *   ⑥ H1: 3 个 Feign Client 都有 fallback, product/user 挂了走兜底
 *
 * 6 个方法分布:
 *   createOrder        ★ 最复杂, 8 步 + 锁 + 事务 + 事务外发 MQ + 跨服务扣库存
 *   listMyOrders       中等, 批量查 + Map 分组
 *   getOrderDetail     简单
 *   cancelOrder        中等, 锁 + 事务 + 状态机 + 回库存
 *   payOrder           中等, 同 cancel 套路 (但不回库存)
 *   closeOrderByMQ     简单 + 幂等, 没用户上下文 + 回库存
 * ════════════════════════════════════════════════════════════════
 */
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {

    // ─── 同服务依赖 (直接 @Autowired) ───
    @Autowired private ICartItemService cartItemService;        // 同服务: cart 在 order 模块
    @Autowired private IOrderItemService orderItemService;
    @Autowired private OrdersMapper ordersMapper;
    @Autowired private OrderItemMapper orderItemMapper;

    // ─── 跨服务依赖 (Feign) ───
    @Autowired private UserFeignClient userFeignClient;         // 查地址
    @Autowired private ProductFeignClient productFeignClient;   // 查商品

    // ─── 基础设施 ───
    @Autowired private RedisLockUtil redisLockUtil;
    @Autowired private TransactionTemplate transactionTemplate; // 控制事务边界 (事务提交后才发 MQ)
    @Autowired private RabbitTemplate rabbitTemplate;

    // ════════════════════════════════════════════════════════════
    // ① 创建订单 (大头, 7 步 + 锁 + 事务 + 事务外发 MQ)
    // ════════════════════════════════════════════════════════════
    @Override
    @GlobalTransactional(name = "createOrder-tx", rollbackFor = Exception.class)
    public Map<String, Object> createOrder(Long userId, CreateOrderDTO dto) {

        // ⭐ G5: @GlobalTransactional 入口
        //   - Seata TM 在这分配全局 XID
        //   - Feign 自动透传 XID 到 product
        //   - product 扣库存写 undo_log, 一阶段本地提交
        //   - 本方法抛任何异常 → TC 反向通知 product 走 undo_log 回滚 stock
        //   - 内部 TransactionTemplate 不动 (它是 order 本地事务,
        //     作为全局事务的一个分支自动管理)

        // 同用户 10 秒内只能下一单 (防重复下单, 防双击)
        String lockKey = "lock:order:user:" + userId;
        String owner = redisLockUtil.tryLock(lockKey, 10);
        if (owner == null) {
            throw new BusinessException(429, "操作太频繁, 请稍后再试");
        }

        try {
            // ⭐ TransactionTemplate 而非 @Transactional 注解:
            //    需要在事务【提交后】发 MQ (防止事务回滚但 MQ 已发出 → 幽灵消息)
            Map<String, Object> orderResult = transactionTemplate.execute(status -> {

                // ─── 第 1 步: 参数校验 ───────────────────
                if (dto.getCartItemIds() == null || dto.getCartItemIds().isEmpty()) {
                    throw new BusinessException(400, "请选择要购买的商品");
                }
                if (dto.getAddressId() == null) {
                    throw new BusinessException(400, "请选择收货地址");
                }

                // ─── 第 2 步: 地址校验 (Feign 调 user 服务) ─
                // ⭐ SEC.11: userId 不再当形参传; FeignAuthInterceptor 会从 SecurityContextHolder
                //    取出 userId 自动塞 X-User-Id 头, 下游 user 服务照常校验
                Result<Map<String, Object>> addrResp = userFeignClient.getAddress(dto.getAddressId());
                if (addrResp == null || addrResp.getCode() != 200 || addrResp.getData() == null) {
                    throw new BusinessException(403, "收货地址无效");
                }
                Map<String, Object> address = addrResp.getData();
                // user 服务的 detail 接口已经做了"是否你的"校验, Feign 这里不用再判 userId

                // ─── 第 3 步: 购物车校验 (同服务) ───────
                List<CartItem> cartItems = cartItemService.listByIds(dto.getCartItemIds());
                if (cartItems.size() != dto.getCartItemIds().size()) {
                    throw new BusinessException(400, "购物车项不存在");
                }
                for (CartItem ci : cartItems) {
                    if (!ci.getUserId().equals(userId)) {
                        throw new BusinessException(403, "无权操作他人购物车");
                    }
                }

                // ─── 第 4 步: 批量查商品 (Feign 调 product 服务) ─
                // 性能注意: 这里 N 次 HTTP, 生产应给 product 加 batch 接口
                Map<Long, Map<String, Object>> productMap = new HashMap<>();
                for (CartItem ci : cartItems) {
                    Result<Map<String, Object>> pResp = productFeignClient.getById(ci.getProductId());
                    if (pResp == null || pResp.getCode() != 200 || pResp.getData() == null) {
                        throw new BusinessException(400, "商品不存在: id=" + ci.getProductId());
                    }
                    productMap.put(ci.getProductId(), pResp.getData());
                }

                // ─── 第 5 步: 计算总价 + 构造 OrderItem (含商品快照) ─
                BigDecimal totalAmount = BigDecimal.ZERO;
                List<OrderItem> orderItems = new ArrayList<>();

                for (CartItem ci : cartItems) {
                    Map<String, Object> p = productMap.get(ci.getProductId());

                    // 商品状态校验 (status=0 已下架)
                    Object statusObj = p.get("status");
                    if (statusObj != null && Integer.parseInt(statusObj.toString()) == 0) {
                        throw new BusinessException(400, "商品已下架: " + p.get("name"));
                    }

                    // 取价格 (Map 取值要 toString 转 BigDecimal, 防 Double 精度问题)
                    BigDecimal price = new BigDecimal(p.get("price").toString());

                    // 小计 = 单价 × 数量, 必须 .multiply 不能 *
                    BigDecimal subtotal = price.multiply(BigDecimal.valueOf(ci.getQuantity()));
                    totalAmount = totalAmount.add(subtotal);

                    // ⭐ 商品快照 (下单瞬间冻结, 商品改名改价不影响这单)
                    OrderItem oi = new OrderItem();
                    oi.setProductId(ci.getProductId());
                    oi.setProductName((String) p.get("name"));
                    oi.setProductImage((String) p.get("coverImage"));
                    oi.setPrice(price);
                    oi.setQuantity(ci.getQuantity());
                    oi.setSubtotal(subtotal);
                    orderItems.add(oi);
                }

                // 生成订单号: yyyyMMddHHmmss + userId + 4 位随机
                String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                        + userId + String.format("%04d", new Random().nextInt(10000));

                // ⭐ G8: 此刻 totalAmount 是【原始金额】, 还没扣券
                BigDecimal originalAmount = totalAmount;

                // 构造 Orders 主表 (地址快照: 拼省+市+区+详细)
                Orders order = new Orders();
                order.setOrderNo(orderNo);
                order.setUserId(userId);
                order.setTotalAmount(originalAmount);     // 先按原价存, 后面用券再 update
                order.setDiscountAmount(BigDecimal.ZERO); // 默认 0, 用券后改
                order.setStatus(OrderStatus.UNPAID);
                order.setReceiver((String) address.get("receiver"));
                order.setPhone((String) address.get("phone"));
                order.setAddress("" + address.get("province") + address.get("city")
                        + address.get("district") + address.get("detail"));
                order.setRemark(dto.getRemark());

                this.save(order);   // ⭐ MP 自动回填 id 到 order.id

                // ─── 第 5.5 步: G8 用券 (可选) ──────────
                // 时序设计:
                //   先 save 订单拿 id, 再调 Feign 用券 (需要 orderId 参数)
                //   useCoupon 抛业务异常 → @GlobalTransactional 回滚 order 库 INSERT
                //   ⚠ 教学限制: user 服务【没接 Seata】, 万一这里成功但下面扣库存失败,
                //     user 库的 user_coupon 状态变更【不会自动回滚】 (留下脏数据)
                //     生产应让 user 服务也接 Seata, 或用 MQ 异步补偿
                if (dto.getUserCouponId() != null) {
                    UseCouponDTO useDto = new UseCouponDTO();
                    useDto.setUserId(userId);
                    useDto.setUserCouponId(dto.getUserCouponId());
                    useDto.setOrderAmount(originalAmount);
                    useDto.setOrderId(order.getId());

                    Result<BigDecimal> useResp = userFeignClient.useCoupon(useDto);
                    if (useResp == null || useResp.getCode() != 200) {
                        throw new BusinessException(useResp == null ? 503 : useResp.getCode(),
                                useResp == null ? "用券失败" : useResp.getMessage());
                    }
                    BigDecimal discountAmount = useResp.getData();
                    BigDecimal finalAmount = originalAmount.subtract(discountAmount);
                    // 防御: 抵扣不能超过原价 (理论上不会, 但保险)
                    if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
                        finalAmount = BigDecimal.ZERO;
                    }
                    // 回写订单: 实际应付 + 抵扣金额 + 用了哪张券
                    order.setUserCouponId(dto.getUserCouponId());
                    order.setDiscountAmount(discountAmount);
                    order.setTotalAmount(finalAmount);
                    this.updateById(order);
                }

                // ─── 第 6 步: 批量保存 OrderItem (orderId 现在才有) ─
                for (OrderItem oi : orderItems) {
                    oi.setOrderId(order.getId());
                }
                orderItemService.saveBatch(orderItems);

                // ─── 第 7 步: 扣库存(G3.10 补) ──────────
                // ⭐ 跨服务调 product 服务的原子 SQL UPDATE...WHERE stock>=qty
                //    rows=0 表示 stock 不够, 抛业务异常 → @Transactional 回滚之前 save 的订单/明细
                //    注意: 这里只回滚 order 库的事务, 已扣的 product 库不会自动回滚 (分布式事务问题, 留 Seata 解)
                for (CartItem ci : cartItems) {
                    try {
                        Result<Integer> deductResp = productFeignClient.deductStock(ci.getProductId(), ci.getQuantity());
                        if (deductResp == null || deductResp.getCode() != 200) {
                            Map<String, Object> p = productMap.get(ci.getProductId());
                            throw new BusinessException(400, "库存不足: " + p.get("name"));
                        }
                    } catch (BusinessException e) {
                        throw e;
                    } catch (Exception feignEx) {
                        Map<String, Object> p = productMap.get(ci.getProductId());
                        throw new BusinessException(400, "库存不足: " + p.get("name"));
                    }
                }

                // ─── 第 8 步: 清掉这几条购物车 ─────────
                cartItemService.removeByIds(dto.getCartItemIds());

                // 返回 orderId + orderNo
                Map<String, Object> result = new HashMap<>();
                result.put("orderNo", orderNo);
                result.put("orderId", order.getId());
                return result;
            });

            // ⭐⭐⭐ 事务外发延迟 MQ (G2 通路真用)
            //    放事务里万一回滚, MQ 已发出 → 死信 30 秒后关一个不存在的订单 = 幽灵消息
            //    放事务外 (execute 返回后), 此时事务已提交, 订单已落库, MQ 安全发出
            Long orderId = (Long) orderResult.get("orderId");
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DELAY_EXCHANGE,        // 延迟交换机
                    RabbitMQConfig.DELAY_ROUTING_KEY,     // routingKey "delay"
                    orderId                                // 消息体: 订单 ID
            );

            return orderResult;

        } finally {
            // 锁必须释放, 否则要等 10 秒 TTL
            redisLockUtil.unlock(lockKey, owner);
        }
    }

    // ════════════════════════════════════════════════════════════
    // ② 我的订单列表 (批量查 + Map 分组组装)
    // ════════════════════════════════════════════════════════════
    @Override
    public List<OrderListVO> listMyOrders(Long userId) {
        // 第 1 步: 查我的所有订单
        QueryWrapper<Orders> ow = new QueryWrapper<>();
        ow.eq("user_id", userId).orderByDesc("create_time");
        List<Orders> orders = this.list(ow);

        if (orders.isEmpty()) {
            return new ArrayList<>();
        }

        // 第 2 步: 收集 orderIds, 批量查所有明细 (SQL: WHERE order_id IN (1,2,3))
        List<Long> orderIds = new ArrayList<>();
        for (Orders o : orders) {
            orderIds.add(o.getId());
        }
        QueryWrapper<OrderItem> iw = new QueryWrapper<>();
        iw.in("order_id", orderIds);
        List<OrderItem> allItems = orderItemService.list(iw);

        // 第 3 步: 按 orderId 分组成 Map<orderId, List<明细>>
        // computeIfAbsent: key 不存在就 put 空 list, 返回 list, 然后直接 .add(item)
        Map<Long, List<OrderItem>> itemMap = new HashMap<>();
        for (OrderItem item : allItems) {
            itemMap.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
        }

        // 第 4 步: 循环订单组装 VO
        List<OrderListVO> result = new ArrayList<>();
        for (Orders o : orders) {
            OrderListVO vo = new OrderListVO();
            vo.setOrderId(o.getId());
            vo.setOrderNo(o.getOrderNo());
            vo.setStatus(o.getStatus());
            vo.setStatusDesc(statusDesc(o.getStatus()));   // 翻译中文
            vo.setTotalAmount(o.getTotalAmount());
            vo.setReceiver(o.getReceiver());
            vo.setAddress(o.getAddress());
            vo.setCreateTime(o.getCreateTime());

            // 组装明细列表
            List<OrderItem> myItems = itemMap.getOrDefault(o.getId(), new ArrayList<>());
            List<OrderItemVO> itemVOs = new ArrayList<>();
            for (OrderItem item : myItems) {
                OrderItemVO ivo = new OrderItemVO();
                ivo.setOrderItemId(item.getId());
                ivo.setProductId(item.getProductId());
                ivo.setProductName(item.getProductName());
                ivo.setProductImage(item.getProductImage());
                ivo.setPrice(item.getPrice());
                ivo.setQuantity(item.getQuantity());
                ivo.setSubtotal(item.getSubtotal());
                itemVOs.add(ivo);
            }
            vo.setItems(itemVOs);
            result.add(vo);
        }
        return result;
    }

    /** 状态码翻译成中文 (私有辅助) */
    private String statusDesc(Byte status) {
        if (status == null) return "未知";
        switch (status) {
            case 0:  return "待付款";
            case 1:  return "已付款";
            case 2:  return "已发货";
            case 3:  return "已完成";
            case 4:  return "已取消";
            default: return "未知";
        }
    }

    // ════════════════════════════════════════════════════════════
    // ③ 订单详情
    // ════════════════════════════════════════════════════════════
    @Override
    public OrderDetailVO getOrderDetail(Long userId, Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该订单");
        }

        // 查明细
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );

        List<OrderItemVO> itemVOs = new ArrayList<>();
        for (OrderItem item : items) {
            OrderItemVO ivo = new OrderItemVO();
            ivo.setOrderItemId(item.getId());
            ivo.setProductId(item.getProductId());
            ivo.setProductName(item.getProductName());
            ivo.setProductImage(item.getProductImage());
            ivo.setPrice(item.getPrice());
            ivo.setQuantity(item.getQuantity());
            ivo.setSubtotal(item.getSubtotal());
            itemVOs.add(ivo);
        }

        // BeanUtils 拷主表字段 (orderNo/totalAmount/receiver/...)
        OrderDetailVO vo = new OrderDetailVO();
        BeanUtils.copyProperties(order, vo);
        vo.setOrderId(order.getId());     // 主表叫 id, vo 字段叫 orderId, 手动设
        vo.setStatusDesc(statusDesc(order.getStatus()));
        vo.setItems(itemVOs);
        return vo;
    }

    // ════════════════════════════════════════════════════════════
    // ③.5 ADMIN 用: 同上, 跳过 userId 越权校验
    // ════════════════════════════════════════════════════════════
    @Override
    public OrderDetailVO getOrderDetailForAdmin(Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在");
        }
        // ⚠ 跟 getOrderDetail 的区别: 没有 userId 越权校验
        // 管理员可以看任何用户的订单, 网关 AuthGlobalFilter 已挡了 role!=1

        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        List<OrderItemVO> itemVOs = new ArrayList<>();
        for (OrderItem item : items) {
            OrderItemVO ivo = new OrderItemVO();
            ivo.setOrderItemId(item.getId());
            ivo.setProductId(item.getProductId());
            ivo.setProductName(item.getProductName());
            ivo.setProductImage(item.getProductImage());
            ivo.setPrice(item.getPrice());
            ivo.setQuantity(item.getQuantity());
            ivo.setSubtotal(item.getSubtotal());
            itemVOs.add(ivo);
        }

        OrderDetailVO vo = new OrderDetailVO();
        BeanUtils.copyProperties(order, vo);
        vo.setOrderId(order.getId());
        vo.setStatusDesc(statusDesc(order.getStatus()));
        vo.setItems(itemVOs);
        return vo;
    }

    // ════════════════════════════════════════════════════════════
    // ④ 用户手动取消订单
    // ════════════════════════════════════════════════════════════
    @Override
    public void cancelOrder(Long userId, Long orderId) {
        // 订单级锁 (防双击取消)
        String lockKey = "lock:order:cancel:" + orderId;
        String owner = redisLockUtil.tryLock(lockKey, 10);
        if (owner == null) {
            throw new BusinessException(429, "操作太频繁");
        }
        try {
            transactionTemplate.execute(status -> {
                Orders order = ordersMapper.selectById(orderId);
                if (order == null) throw new BusinessException(404, "订单不存在");
                if (!order.getUserId().equals(userId)) throw new BusinessException(403, "无权操作");

                // ⭐ SEC-2 原子状态机: UPDATE orders SET status=4 WHERE id=? AND status=0
                //    老写法是"先查→Java判断→updateById"(check-then-act), 两个并发请求会都通过判断;
                //    而且 cancel/pay/MQ关单 的 Redis 锁 key 各不相同, 根本互斥不了。
                //    现在把"判断"塞进 UPDATE 的 WHERE 里, 数据库行锁保证只有一个请求改成功(rows=1),
                //    输家 rows=0 → 报错。这跟扣库存 UPDATE...WHERE stock>=qty 是同一个套路。
                LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<>();
                uw.eq(Orders::getId, orderId)
                  .eq(Orders::getStatus, OrderStatus.UNPAID)        // WHERE 里的"期望旧状态"
                  .set(Orders::getStatus, OrderStatus.CANCELLED);   // SET 新状态
                int rows = ordersMapper.update(null, uw);           // 第一参 null = 只用 wrapper 的 set
                if (rows == 0) {
                    // 抢输了(已被支付/已被 MQ 关掉/重复点击) → 明确拒绝, 绝不重复回库存
                    throw new BusinessException(400, "当前订单状态不可取消");
                }

                // ── G3.10 回库存: 查该订单全部明细, 逐个 Feign 调 product 还库存 ──
                QueryWrapper<OrderItem> itemW = new QueryWrapper<>();
                itemW.eq("order_id", orderId);
                List<OrderItem> items = orderItemMapper.selectList(itemW);
                for (OrderItem item : items) {
                    productFeignClient.restoreStock(item.getProductId(), item.getQuantity());
                }

                // ── G8 退券: 用了券就 afterCommit 调 user.refundCoupon ──
                //   afterCommit 原因 (跟 G7 review 同坑):
                //   user 端如果立刻查 orders 表确认状态, 此时 UPDATE 还没 commit, 看不到 CANCELLED.
                //   afterCommit 时机最干净
                Long ucId = order.getUserCouponId();
                if (ucId != null) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            userFeignClient.refundCoupon(ucId);
                        }
                    });
                }
                return null;
            });
        } finally {
            redisLockUtil.unlock(lockKey, owner);
        }
    }

    // ════════════════════════════════════════════════════════════
    // ⑤ 标记已付款 (本地模拟, 不接真支付)
    // ════════════════════════════════════════════════════════════
    @Override
    public void payOrder(Long userId, Long orderId) {
        // ⭐ key 用 orderId 而非 userId: 允许同时支付多个订单, 但同一单只能一次
        String lockKey = "lock:order:pay:" + orderId;
        String owner = redisLockUtil.tryLock(lockKey, 10);
        if (owner == null) {
            throw new BusinessException(429, "操作太频繁");
        }
        try {
            transactionTemplate.execute(status -> {
                Orders order = ordersMapper.selectById(orderId);
                if (order == null) throw new BusinessException(404, "订单不存在");
                if (!order.getUserId().equals(userId)) throw new BusinessException(403, "无权操作");

                // ⭐ SEC-2 原子状态机: 0(待付款)→1(已付款), 条件 UPDATE 一步到位。
                //    最关键的对手是 MQ 自动关单: 用户在第 30 分钟同时点支付,
                //    老写法可能出现"MQ 关单回了库存, 支付又把状态改成已付款"的脏局面;
                //    现在两边都走条件 UPDATE, 数据库保证只有一方赢。
                LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<>();
                uw.eq(Orders::getId, orderId)
                  .eq(Orders::getStatus, OrderStatus.UNPAID)
                  .set(Orders::getStatus, OrderStatus.PAID)
                  .set(Orders::getPayTime, LocalDateTime.now());   // 附带字段跟状态同一条 UPDATE, 原子生效
                int rows = ordersMapper.update(null, uw);
                if (rows == 0) {
                    throw new BusinessException(400, "订单不可支付");
                }
                return null;
            });
        } finally {
            redisLockUtil.unlock(lockKey, owner);
        }
    }

    // ════════════════════════════════════════════════════════════
    // ⑥ MQ 消费者关单 (注意: 没 userId, 必须幂等)
    // ════════════════════════════════════════════════════════════
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeOrderByMQ(Long orderId) {
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) {
            return;   // 订单不存在 (可能被手动取消并物理删了), 跳过
        }

        // ⭐ SEC-2 幂等核心升级: 老写法"读 status 判断 → updateById"在 MQ 消费者里最危险,
        //    因为这个方法【没有任何锁】, 跟用户支付/手动取消完全并发:
        //      时刻T1: MQ 读到 status=0     时刻T1: 用户支付也读到 status=0
        //      时刻T2: MQ 改成 4 + 回库存    时刻T2: 支付改成 1  ← 谁后写谁覆盖, 库存还被多回了一次
        //    改成条件 UPDATE 后: 数据库只让一个 UPDATE 命中(rows=1), 输家 rows=0 安静退出。
        //    消息重复投递天然幂等: 第二次 CAS 时 status 已是 4, rows=0 直接 return。
        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<>();
        uw.eq(Orders::getId, orderId)
          .eq(Orders::getStatus, OrderStatus.UNPAID)
          .set(Orders::getStatus, OrderStatus.CANCELLED);
        int rows = ordersMapper.update(null, uw);
        if (rows == 0) {
            return;   // 已付款/已取消/重复消息 → 什么都不做(尤其是不能回库存!)
        }

        // ── G3.10 回库存: 同 cancelOrder 套路 ──
        QueryWrapper<OrderItem> itemW = new QueryWrapper<>();
        itemW.eq("order_id", orderId);
        List<OrderItem> items = orderItemMapper.selectList(itemW);
        for (OrderItem item : items) {
            productFeignClient.restoreStock(item.getProductId(), item.getQuantity());
        }

        // ── G8 退券: afterCommit 调 user.refundCoupon ──
        Long ucId = order.getUserCouponId();
        if (ucId != null) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    userFeignClient.refundCoupon(ucId);
                }
            });
        }
    }

    // ════════════════════════════════════════════════════════════
    // ⑥.5 支付回调标记已付款 (payment 服务 Feign internal 调, 无 userId)
    // ════════════════════════════════════════════════════════════
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaidByNotify(Long orderId) {
        // ⭐ 跟 payOrder 同款 CAS, 但没有 userId 校验、没有 Redis 锁:
        //    调用方是 payment 服务(可信), 幂等完全靠 WHERE status=0 兜住支付宝的重复回调。
        //    时序对手是"MQ 30分钟关单": 若关单先赢(status已变4), 这里 rows=0, 支付不会把已关订单改活。
        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<>();
        uw.eq(Orders::getId, orderId)
          .eq(Orders::getStatus, OrderStatus.UNPAID)
          .set(Orders::getStatus, OrderStatus.PAID)
          .set(Orders::getPayTime, LocalDateTime.now());
        int rows = ordersMapper.update(null, uw);
        // rows=1: 本次真的把订单支付掉了; rows=0: 已付款/已取消/重复回调 → 幂等返回 false
        return rows == 1;
    }

    // ════════════════════════════════════════════════════════════
    // ⑥.6 退款成功标记订单退款/关闭 (payment 服务 Feign internal 调)
    // ════════════════════════════════════════════════════════════
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRefundedByNotify(Long orderId) {
        // CAS: 已付款(1)或已发货(2) → 已取消(4)。in 条件让"发货后退款"也能覆盖。
        // 待付款(0)不该走退款(它走的是取消); 已完成(3)/已取消(4)不动。
        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<>();
        uw.eq(Orders::getId, orderId)
          .in(Orders::getStatus, OrderStatus.PAID, OrderStatus.SHIPPED)
          .set(Orders::getStatus, OrderStatus.CANCELLED);
        int rows = ordersMapper.update(null, uw);
        // ⚠ V1 从简: 这里没回补库存/退券。生产应像 cancelOrder 那样查明细逐个 restoreStock + 退券。
        return rows == 1;
    }

    // ════════════════════════════════════════════════════════════
    // ⑦ G6 物流: 发货 (管理员调用)
    // ════════════════════════════════════════════════════════════
    @Override
    public void shipOrder(Long orderId, ShipOrderDTO dto) {
        // ─────────────────────────────────────────────────────────
        // TODO 用户填: 状态机 4 步模板 (参考 cancelOrder 行 380~390)
        //
        //   第 1 步: 查订单, 为空抛 BusinessException(404, "订单不存在")
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) throw new BusinessException(404, "订单不存在");
        //   第 2 步: 跳过 user_id 校验 (admin 不需要)
        //   第 3+4 步合并: ⭐ SEC-2 原子状态机 1(已付款)→2(已发货)
        //   老写法"查→判断→updateById"两个管理员同时点发货会都成功(还可能互相覆盖运单号);
        //   条件 UPDATE 保证只有第一次点击生效, 第二次 rows=0 报错。
        // ─────────────────────────────────────────────────────────
        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<>();
        uw.eq(Orders::getId, orderId)
          .eq(Orders::getStatus, OrderStatus.PAID)
          .set(Orders::getStatus, OrderStatus.SHIPPED)
          .set(Orders::getShipTime, LocalDateTime.now())
          .set(Orders::getLogisticsCompany, dto.getLogisticsCompany())
          .set(Orders::getLogisticsNo, dto.getLogisticsNo());
        int rows = ordersMapper.update(null, uw);
        if (rows == 0) {
            throw new BusinessException(400, "订单状态不可发货");
        }
    }

    // ════════════════════════════════════════════════════════════
    // ⑧ G6 物流: 签收 (用户主动)
    // ════════════════════════════════════════════════════════════
    @Override
    public void signOrder(Long userId, Long orderId) {
        // ─────────────────────────────────────────────────────────
        // TODO 用户填: 状态机 4 步模板 (参考 cancelOrder)
        //
        //   第 1 步: 查订单, 为空抛 BusinessException(404, "订单不存在")

        //   第 2 步: 越权校验 — order.getUserId().equals(userId)
        //            不匹配抛 BusinessException(403, "无权操作")

        //   第 3 步: 状态机前置 — 必须 == OrderStatus.SHIPPED (=2)
        //            否则抛 BusinessException(400, "订单状态不可签收")

        //   第 4 步: setStatus(OrderStatus.COMPLETED) + setFinishTime(now)
        //            ordersMapper.updateById(order);
        //
        // 提示: OrderStatus.COMPLETED 是已定义的常量 (=3)
        // ─────────────────────────────────────────────────────────
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) throw new BusinessException(404, "订单不存在");

        // 第 2 步: 越权校验 (用户类操作必须做!)
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作");
        }

        // 第 3+4 步合并: ⭐ SEC-2 原子状态机 2(已发货)→3(已完成), 双击签收只成功一次
        LambdaUpdateWrapper<Orders> uw = new LambdaUpdateWrapper<>();
        uw.eq(Orders::getId, orderId)
          .eq(Orders::getStatus, OrderStatus.SHIPPED)
          .set(Orders::getStatus, OrderStatus.COMPLETED)
          .set(Orders::getFinishTime, LocalDateTime.now());
        int rows = ordersMapper.update(null, uw);
        if (rows == 0) {
            throw new BusinessException(400, "订单状态不可签收");
        }
    }
}

