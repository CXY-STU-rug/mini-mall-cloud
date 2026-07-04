package com.minimall.payment.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝客户端配置。
 * <p>
 * 核心产出: 一个 AlipayClient Bean —— 支付服务的"发动机"。
 * 之后创建支付单(生成支付页)、发起退款、查询交易, 全都调它的 execute(request)。
 * <p>
 * DefaultAlipayClient 的 7 个参数, 一一对应支付宝对接的要素:
 *   serverUrl        网关地址 (沙箱/正式不同)
 *   appId            应用 APPID
 *   privateKey       应用私钥 —— 给我方发出的请求【签名】(证明"这请求真是我发的")
 *   format           数据格式 json
 *   charset          编码 utf-8
 *   alipayPublicKey  支付宝公钥 —— 【验】支付宝返回/回调的签名(证明"这响应真是支付宝发的")
 *   signType         签名算法 RSA2
 * <p>
 * 一句话记牢: 私钥签我方请求, 支付宝公钥验支付宝响应。这一对非对称密钥就是支付安全的地基。
 */
@Configuration
@RequiredArgsConstructor   // 让 final 字段走构造注入, 拿到 AlipayProperties
public class AlipayConfig {

    private final AlipayProperties props;   // 前面写的配置属性类, 值来自 application.yml 的 alipay.*

    @Bean
    public AlipayClient alipayClient() {
        // new 出客户端: 参数顺序不能错, 错了要么签名失败要么验签失败
        return new DefaultAlipayClient(
                props.getGatewayUrl(),        // 网关
                props.getAppId(),             // APPID
                props.getPrivateKey(),        // 应用私钥(签名)
                props.getFormat(),            // json
                props.getCharset(),           // utf-8
                props.getAlipayPublicKey(),   // 支付宝公钥(验签)
                props.getSignType()           // RSA2
        );
    }
    // 注释: 这个 Bean 是单例, 全服务共用一个。DefaultAlipayClient 内部线程安全, 不用每次 new。
}
