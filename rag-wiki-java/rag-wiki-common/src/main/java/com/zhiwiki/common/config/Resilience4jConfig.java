package com.zhiwiki.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j 熔断降级配置
 * 
 * 核心能力：
 * 1. 熔断器（Circuit Breaker）：自动熔断故障服务，防止级联故障
 * 2. 重试（Retry）：自动重试临时性失败
 * 3. 限流（Rate Limiter）：限制调用频率
 * 4. 隔离仓（Bulkhead）：限制并发调用数
 * 
 * 熔断器状态机：
 * CLOSED -> OPEN（失败率超过阈值）
 * OPEN -> HALF_OPEN（等待时间窗口到期）
 * HALF_OPEN -> CLOSED（探测成功）或 OPEN（探测失败）
 */
@Slf4j
@Configuration
public class Resilience4jConfig {

    /**
     * 熔断器注册表
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        // 默认熔断器配置
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                // 失败率阈值（百分比），超过则打开熔断器
                .failureRateThreshold(50)
                // 慢调用阈值（秒）
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                // 慢调用比例阈值（百分比）
                .slowCallRateThreshold(80)
                // 熔断器打开后等待时间窗口
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // 半开状态允许的请求数
                .permittedNumberOfCallsInHalfOpenState(5)
                // 滑动窗口大小
                .slidingWindowSize(20)
                // 滑动窗口类型：COUNT_BASED 或 TIME_BASED
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                // 最小请求量（低于此值不计算失败率）
                .minimumNumberOfCalls(10)
                // 自动从OPEN转换到HALF_OPEN
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(defaultConfig);

        // Python AI服务专用熔断器（更宽松，AI服务延迟较高）
        CircuitBreakerConfig aiServiceConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(60)
                .slowCallDurationThreshold(Duration.ofSeconds(10))
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        registry.register("pythonAiService", aiServiceConfig);

        // 数据库服务熔断器（更严格，数据库故障应快速失败）
        CircuitBreakerConfig dbServiceConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(30)
                .slowCallDurationThreshold(Duration.ofSeconds(2))
                .slowCallRateThreshold(60)
                .waitDurationInOpenState(Duration.ofSeconds(20))
                .permittedNumberOfCallsInHalfOpenState(5)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        registry.register("databaseService", dbServiceConfig);

        // 注册熔断器事件监听
        registry.getAllCircuitBreakers().forEach(cb -> {
            cb.getEventPublisher()
                    .onStateTransition(event -> log.warn("[熔断器状态变更] {} : {} -> {}", 
                            cb.getName(), event.getStateTransition().getFromState(), 
                            event.getStateTransition().getToState()))
                    .onError(event -> log.error("[熔断器错误] {} : {}", cb.getName(), event.getThrowable().getMessage()))
                    .onSlowCall(event -> log.warn("[慢调用] {} : duration={}ms", 
                            cb.getName(), event.getElapsedDuration().toMillis()));
        });

        return registry;
    }

    /**
     * 重试注册表
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryOnException(e -> {
                    // 只重试临时性异常
                    return e instanceof java.io.IOException 
                            || e instanceof java.util.concurrent.TimeoutException
                            || e instanceof org.springframework.web.client.ResourceAccessException;
                })
                .build();

        RetryRegistry registry = RetryRegistry.of(defaultConfig);

        // AI服务重试配置（更长等待时间）
        RetryConfig aiRetryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(1000))
                .retryOnException(e -> e instanceof java.util.concurrent.TimeoutException 
                        || e instanceof java.io.IOException)
                .build();

        registry.register("pythonAiService", aiRetryConfig);

        return registry;
    }
}
