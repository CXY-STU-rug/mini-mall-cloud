package com.minimall.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝配置属性 (映射 application.yml 里 alipay.* 那一段)。
 * <p>
 * @ConfigurationProperties(prefix="alipay") 把 yml 的 alipay.app-id 等
 * 自动按"短横线转驼峰"绑到这些字段上 (app-id → appId)。
 * <p>
 * 三个密钥的角色 (对接支付宝最容易搞混, 记牢):
 *   privateKey       应用私钥 —— 给我们发出的请求【签名】(保密)
 *   alipayPublicKey  支付宝公钥 —— 【验】支付宝回调的签名
 *   (应用公钥不在这里, 它上传到支付宝平台, 由支付宝拿去验我们的签名)
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 沙箱应用 APPID */
    private String appId;

    /** 应用私钥 (签名用, 保密) */
    private String privateKey;

    /** 支付宝公钥 (验签支付宝回调用) */
    private String alipayPublicKey;

    /** 网关地址 (沙箱/正式不同) */
    private String gatewayUrl;

    /** 支付异步回调地址 (支付宝 → 我方, 必须公网可达) */
    private String notifyUrl;

    /** 退款异步回调地址 */
    private String refundNotifyUrl;

    /** 同步跳回地址 (用户浏览器跳回, 仅展示不可信) */
    private String returnUrl;

    /** 签名类型, 固定 RSA2 */
    private String signType;

    /** 编码, utf-8 */
    private String charset;

    /** 数据格式, json */
    private String format;
}
