package com.minimall.common.core.exception;

import com.minimall.common.core.domain.Result;
import lombok.extern.slf4j.Slf4j;
// ⚠️ 不能 import jakarta.servlet.http.HttpServletRequest
// common-core 必须是【纯通用】，绑定 Servlet 会让 WebFlux 网关启动崩溃
// 想打 URI 日志？用 MDC 或在各业务模块自己再扩展
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器
 *
 * 工作原理（Spring 内部机制）：
 *   ① Controller 抛出任何异常
 *   ② Spring 自动找匹配的 @ExceptionHandler 方法
 *   ③ 调用该方法，把方法返回值序列化成 JSON 写回响应
 *   ④ Controller 上的 @RestController 让返回值自动转 JSON，
 *     @RestControllerAdvice 让这里的方法也享受同样待遇
 *
 * 异常优先级（@ExceptionHandler 匹配规则）：
 *   越具体的类 优先级越高
 *   BusinessException 比 Exception 优先匹配
 *   所以 handleException 是【兜底】，只在前面没匹配时才生效
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 ① 业务异常（我们自己 throw 的）
     *
     * 这是 mini-mall 90% 的异常来源
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        // warn 级别：不是系统错误，是业务规则触发，正常情况
        log.warn("[业务异常] 错误码={}, 消息={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理 ② @Valid 参数校验失败（@RequestBody DTO 类）
     *
     * 触发场景：
     *   Controller 方法签名：login(@Valid @RequestBody UserLoginDTO dto)
     *   dto 里 @NotBlank 字段为空 → 自动抛 MethodArgumentNotValidException
     *
     * 我们要把"字段名+校验失败原因"拼出来返给前端
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        // 拿到第一个字段错误（一般够用，多个错误可以拼接）
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + " " + fieldError.getDefaultMessage()
                : "参数校验失败";
        log.warn("[参数校验失败] {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理 ③ @Valid 校验表单/Query 参数失败
     *
     * 触发场景：
     *   Controller 签名：list(@Valid PageDTO dto)（不带 @RequestBody）
     *   字段校验失败 → 抛 BindException（不是 MethodArgumentNotValidException）
     *
     * 跟 ② 几乎一样，只是异常类型不同（Spring 设计的坑）
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getField() + " " + fieldError.getDefaultMessage()
                : "参数绑定失败";
        log.warn("[参数绑定失败] {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理 ③.5 路径/Query 参数类型不匹配（如 /order/{orderId} 传了 "NaN"、"abc"）
     *
     * 触发场景：
     *   Controller 签名 detail(@PathVariable Long orderId)，前端却请求 /order/NaN
     *   Spring 无法把字符串 "NaN" 转成 Long → 抛 MethodArgumentTypeMismatchException
     *
     * 为什么单独处理：
     *   这【不是系统故障】，是前端传了非法参数。若落到 ④ 兜底会返回 500"系统繁忙"，
     *   既吓人又误导排查。这里明确返回 400 + 哪个参数、什么非法值，一眼定位是前端问题。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String name = e.getName();          // 参数名，如 orderId
        Object value = e.getValue();        // 前端传来的非法值，如 "NaN"
        String message = "参数 " + name + " 格式非法: " + value;
        log.warn("[参数类型不匹配] {}", message);
        return Result.error(400, message);
    }

    /**
     * 处理 ④ 兜底：所有未捕获的异常
     *
     * 触发场景：
     *   NullPointerException / 数据库连接超时 / Redis 挂了 / 代码 bug
     *
     * 处理原则：
     *   - 用 error 级别记日志（要排查的）
     *   - 给前端返回友好提示（不暴露技术细节，防 SQL 注入信息泄露）
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // G6 修复: Seata AOP 切面把 BusinessException 包装成 RuntimeException
        //   (堆栈见 io.seata.spring.annotation.AdapterInvocationWrapper.proceed)
        // 兜底前解包 cause 链, 找到原始 BusinessException 就按业务异常返
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof BusinessException be) {
                log.warn("[业务异常-解包] 错误码={}, 消息={}", be.getCode(), be.getMessage());
                return Result.error(be.getCode(), be.getMessage());
            }
            cause = cause.getCause();
        }
        log.error("[系统异常]", e);
        return Result.error(500, "系统繁忙，请稍后再试");
    }
}
