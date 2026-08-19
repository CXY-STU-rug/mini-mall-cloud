package com.minimall.user.controller;

import com.minimall.common.core.context.SecurityContextHolder;
import com.minimall.common.core.domain.Result;
import com.minimall.common.core.exception.BusinessException;
import com.minimall.user.client.ProductFeignClient;
import com.minimall.user.dto.ChangePasswordDTO;
import com.minimall.user.dto.UserProfileUpdateDTO;
import com.minimall.user.entity.User;
import com.minimall.user.mapper.UserMapper;
import com.minimall.user.service.IUserService;
import com.minimall.user.vo.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * User 业务 Controller (AUTH 阶段后, 不再含 login/register, 那些移到 mini-mall-auth)
 *
 * 剩下端点:
 *   GET /user/me                                        当前用户 id (从 X-User-Id header 取)
 *   GET /user/{id}                                      按 id 查
 *   GET /user/{userId}/with-product/{productId}         Feign 跨服务演示
 *
 * ⚠️ password 兜底:
 *   AUTH 阶段去掉了 entity.User.password 上的 @JsonIgnore (为了让 internal 接口能返密文给 auth).
 *   所以对外端点返 User 时必须显式 setPassword(null), 见下面 getById / with-product.
 */
@RestController
@RequestMapping("/user")
public class UserController {

    /** BCrypt 加密器 —— 无状态线程安全, 全类共享 */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private IUserService userService;

    @Autowired
    private ProductFeignClient productFeignClient;

    /**
     * ⭐ D4 验证接口: 检验 X-User-Id 是否真的从网关透传过来
     */
    @GetMapping("/me")
    public Result<UserProfileVO> me() {                          // ← 改: 返 VO
        Long userId = SecurityContextHolder.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // entity → VO, 用 Spring 自带工具按字段名拷, 敏感字段自动被丢弃
        UserProfileVO vo = new UserProfileVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }
    @PutMapping("/me")
    public Result<Void> updateMe(@Valid @RequestBody UserProfileUpdateDTO dto) {
        // ⭐ TODO ①: 从 SecurityContextHolder 拿当前 userId
        //   提示: 跟 me() 方法一样
        Long userId = SecurityContextHolder.getUserId();

        // 组装更新对象, 只放允许字段, role/status/password 永远不动
        User update = new User();
        update.setId(userId);
        update.setNickname(dto.getNickname());
        update.setPhone(dto.getPhone());
        update.setEmail(dto.getEmail());
        update.setAvatar(dto.getAvatar());
        // ⭐ TODO ②: 调 userService.updateById(update)
        //   提示: MP 默认空字段不更新, dto 没传的字段不会被覆盖
        userService.updateById(update);

        return Result.success();
    }
    /**
     * 修改密码 (已登录用户, 需要提供原密码)
     *
     * 流程:
     *   ① 从 X-User-Id 取当前用户 id
     *   ② 查库拿 BCrypt 密文
     *   ③ matches(oldPassword, 密文) — 不对就 400
     *   ④ 加密新密码 → updateById (只改 password 字段)
     */
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        Long userId = SecurityContextHolder.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        if (!ENCODER.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(ENCODER.encode(dto.getNewPassword()));
        userService.updateById(update);
        return Result.success();
    }

    /** 按 id 查 */
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable("id") Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);   // ⭐ 兜底: 不返密文给前端
        return Result.success(user);
    }

    /**
     * Feign 调用演示: 拼装 [用户 + 商品] 信息
     */
    @GetMapping("/{userId}/with-product/{productId}")
    public Result<Map<String, Object>> getUserWithProduct(@PathVariable Long userId,
                                                          @PathVariable Long productId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);   // ⭐ 兜底

        Result<Map<String, Object>> productResp = productFeignClient.getById(productId);
        if (productResp.getCode() != 200) {
            throw new BusinessException(productResp.getMessage());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("product", productResp.getData());
        return Result.success(data);
    }

}
