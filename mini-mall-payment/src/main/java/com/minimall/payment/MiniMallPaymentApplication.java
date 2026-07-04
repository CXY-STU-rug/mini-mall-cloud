package com.minimall.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.annotation.MapperScan;

/**
 * mini-mall-payment 支付服务启动类
 *
 * 三个注解各管一摊:
 *   @MapperScan          扫 mapper 包, 让 PaymentMapper 等接口生成实现 (连 DB)
 *   @EnableFeignClients  扫 client 包, 让 OrderFeignClient 生成代理 (通知 order 改状态)
 *   @ComponentScan       扩到 com.minimall 根包, 才能拿到 common-core/common-security 的 Bean
 *                        (Result / 全局异常 / SecurityContextHolder 包名都是 com.minimall.common.*)
 */
@SpringBootApplication
@ComponentScan("com.minimall")
@MapperScan("com.minimall.payment.mapper")
@EnableFeignClients(basePackages = "com.minimall.payment.client")
public class MiniMallPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniMallPaymentApplication.class, args);
    }
}
