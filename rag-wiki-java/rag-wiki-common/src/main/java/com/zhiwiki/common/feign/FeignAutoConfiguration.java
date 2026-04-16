package com.zhiwiki.common.feign;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Feign客户端自动配置
 * 所有微服务通过扫描 com.zhiwiki.common.feign 包自动注册远程调用客户端
 */
@Configuration
@EnableFeignClients(basePackages = "com.zhiwiki.common.feign")
public class FeignAutoConfiguration {
}
