package com.minimall.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.user.entity.Address;
import com.minimall.user.service.IAddressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收货地址 Controller (从单体搬过来, 关键改动: UserContext → X-User-Id header)
 *
 * ⭐ 微服务 vs 单体的【上下文传递差异】:
 *
 * ── 单体 ──────────────────────────────────────────────────
 *   ① 前端 Authorization: Bearer xxx → Controller
 *   ② JwtInterceptor 拦截, 解 token, 把 userId 放到 ThreadLocal (UserContext)
 *   ③ Controller 调 UserContext.getUserId() 拿
 *   全程在同一 JVM, ThreadLocal 在同一个线程里通用
 *
 * ── 微服务 (SEC.10 重构后) ─────────────────────────────────
 *   ① 前端 Authorization: Bearer xxx → 【网关 9080】
 *   ② 网关 AuthGlobalFilter 解 token, 把 userId 写进 X-User-Id header
 *   ③ 转发到下游服务 (user-service:9001)
 *   ④ HeaderInterceptor 自动把 X-User-Id 头塞进 SecurityContextHolder (ThreadLocal)
 *   ⑤ Controller 调 SecurityContextHolder.getUserId() 拿 — 写法跟单体一样
 *   微服务里 ThreadLocal 回来了, 但仅在 Controller 当前线程内有效
 *
 * 端点 (网关代理):
 *   GET    /user/address           → 我的地址列表
 *   GET    /user/address/{id}      → 地址详情 (含越权校验)
 *   POST   /user/address           → 新增 (强制写 userId, 不信前端)
 *   PUT    /user/address/{id}      → 修改 (含越权校验)
 *   DELETE /user/address/{id}      → 删除 (含越权校验)
 *
 * 注: 路径前缀 /user 跟 user-service 其他接口 (/user/{id}, /user/login...) 共用
 *     这样网关只需要一条 /user/** → mini-mall-user 路由
 */
@RestController
@RequestMapping("/user/address")
public class AddressController {

    @Autowired
    private IAddressService addressService;

    /**
     * ① 我的地址列表
     *
     * 排序: 默认地址置顶 → 再按创建时间倒序
     */
    @GetMapping
    public Result<List<Address>> list() {
        Long userId = SecurityContextHolder.getUserId();
        QueryWrapper<Address> w = new QueryWrapper<>();
        w.eq("user_id", userId)                    // ⭐ 强制只查自己的
         .orderByDesc("is_default")                // 默认置顶
         .orderByDesc("create_time");

        return Result.success(addressService.list(w));
    }

    /**
     * ② 详情 (含越权校验)
     */
    @GetMapping("/{id}")
    public Result<Address> detail(
            @PathVariable Long id

    ) {
        Long userId= SecurityContextHolder.getUserId();
        return Result.success(getAndCheckOwn(id, userId));
    }

    /**
     * ③ 新增
     *
     * 关键改动 vs 单体:
     *   ① 加 @RequestBody, 前端发 JSON 才能正确反序列化
     *   ② 强制覆盖 userId, 不信前端 body 里塞的 (防止伪造别人地址)
     */
    @PostMapping
    public Result<Address> create(
            @RequestBody Address address
    ) {
        Long userId= SecurityContextHolder.getUserId();
        address.setUserId(userId);       // ⭐ 强制盖, 防伪造
        address.setIsDefault((byte) 0);  // ⭐ 新地址必为非默认, 设默认走 PUT /address/default (互斥保障)
        addressService.save(address);    // MP 自动回填自增 id
        return Result.success(address);
    }

    /**
     * ④ 修改
     */
    @PutMapping("/{id}")
    public Result<Address> update(
            @PathVariable Long id,
            @RequestBody Address address
    ) {
        Long userId= SecurityContextHolder.getUserId();
        // 先校验"这条 id 真是你的", 避免伪造别人的地址 id 改成自己的
        getAndCheckOwn(id, userId);

        // 路径里的 id 是权威 id, 强制覆盖
        address.setId(id);
        address.setUserId(userId);     // 再强制覆盖一次 userId
        address.setIsDefault(null);    // ⭐ 改地址内容时不动 is_default: MP 默认 null 字段不更新, 改默认走 PUT /address/default

        addressService.updateById(address);
        return Result.success(address);
    }

    /**
     * ⑤ 删除 (逻辑删除, isDeleted 字段加了 @TableLogic)
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable Long id
    ) {
        Long userId= SecurityContextHolder.getUserId();
        getAndCheckOwn(id, userId);
        addressService.removeById(id);  // 实际执行 UPDATE ... SET is_deleted=1
        return Result.success();
    }
    @PutMapping("/default")
    public Result<Void> setDefault(@RequestParam Long addressId) {
        Long userId = SecurityContextHolder.getUserId();
        addressService.setDefaultAddress(userId, addressId);
        return Result.success();
    }
    // ═════════════════════════════════════════════════════════
    // 私有工具: 查 + 越权校验
    // ═════════════════════════════════════════════════════════

    /**
     * 按 id 查地址, 同时验证它属于当前用户。
     *
     * 三种结果:
     *   - 不存在        → 404 BusinessException
     *   - 不是当前用户  → 403 BusinessException (越权)
     *   - 校验通过      → 返回 Address 实体
     *
     * 注: 单体里 userId 从 UserContext.getUserId() 隐式拿,
     *     这里改成【显式传参】, 让方法对外部状态零依赖, 更清晰也更易测
     */
    private Address getAndCheckOwn(Long id, Long currentUserId) {
        Address addr = addressService.getById(id);
        if (addr == null) {
            throw new BusinessException(404, "地址不存在");
        }
        if (!addr.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权访问该地址");
        }
        return addr;
    }
}
