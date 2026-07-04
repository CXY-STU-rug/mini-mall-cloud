package com.minimall.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * mini-mall-ai 智能客服 Agent 启动类
 *
 * 注解说明 (对比 payment: 【没有 @MapperScan】——本服务第一期不连数据库,
 *          向量数据存 Redis Stack, 由 LangChain4j 访问, 不走 MyBatis):
 *   @ComponentScan       扩到 com.minimall 根包, 才能拿到 common-core/common-security 的 Bean
 *   @EnableFeignClients  扫 client 包, 让 ProductFeignClient 生成代理 (Task5 调 product 查商品)
 */
@SpringBootApplication
@ComponentScan("com.minimall")
@EnableFeignClients(basePackages = "com.minimall.ai.client")
public class MiniMallAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniMallAiApplication.class, args);
    }
}
