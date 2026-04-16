package com.zhiwiki.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 限流过滤器 - 基于Redis令牌桶算法实现API流控
 * 
 * 限流策略：
 * 1. 全局限流 - 系统总QPS限制
 * 2. 用户级限流 - 单用户QPS限制
 * 3. IP级限流 - 单IP QPS限制
 * 4. 接口级限流 - 敏感接口特殊限制
 * 
 * 配置示例（application.yml）：
 * rag-wiki:
 *   rate-limit:
 *     global-qps: 500
 *     user-qps: 50
 *     ip-qps: 100
 *     sensitive-apis:
 *       /api/ai/rag/query: 20
 *       /api/ai/sandbox/execute: 5
 *       /api/auth/login: 10
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final StringRedisTemplate redisTemplate;

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 限流Redis Key前缀 */
    private static final String RATE_LIMIT_PREFIX = "rag-wiki:rate-limit:";
    
    /** 全局限流QPS（默认500） */
    private static final int GLOBAL_QPS = 500;
    /** 单用户QPS（默认50） */
    private static final int USER_QPS = 50;
    /** 单IP QPS（默认100） */
    private static final int IP_QPS = 100;
    /** 限流窗口（秒） */
    private static final int WINDOW_SECONDS = 1;

    /** 敏感接口限流配置：路径 -> QPS限制 */
    private static final java.util.Map<String, Integer> SENSITIVE_APIS = java.util.Map.of(
            "/api/ai/rag/query", 20,
            "/api/ai/rag/query/stream", 15,
            "/api/ai/sandbox/execute", 5,
            "/api/auth/login", 10,
            "/api/auth/oauth2/callback", 10,
            "/api/ai/agent/submit", 10,
            "/api/ai/enterprise-agent/execute", 5
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 敏感接口限流
        Integer sensitiveQps = getSensitiveApiQps(path);
        if (sensitiveQps != null) {
            String key = RATE_LIMIT_PREFIX + "api:" + path;
            if (!tryAcquire(key, sensitiveQps)) {
                log.warn("敏感接口限流: path={}, qps={}", path, sensitiveQps);
                return rateLimited(exchange, "接口访问过于频繁，请稍后再试");
            }
        }

        // 2. 用户级限流
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            String userKey = RATE_LIMIT_PREFIX + "user:" + userId;
            if (!tryAcquire(userKey, USER_QPS)) {
                log.warn("用户限流: userId={}, qps={}", userId, USER_QPS);
                return rateLimited(exchange, "您的请求过于频繁，请稍后再试");
            }
        }

        // 3. IP级限流
        String clientIp = getClientIp(request);
        if (clientIp != null) {
            String ipKey = RATE_LIMIT_PREFIX + "ip:" + clientIp;
            if (!tryAcquire(ipKey, IP_QPS)) {
                log.warn("IP限流: ip={}, qps={}", clientIp, IP_QPS);
                return rateLimited(exchange, "访问频率超限，请稍后再试");
            }
        }

        // 4. 全局限流
        String globalKey = RATE_LIMIT_PREFIX + "global";
        if (!tryAcquire(globalKey, GLOBAL_QPS)) {
            log.warn("全局限流触发: qps={}", GLOBAL_QPS);
            return rateLimited(exchange, "系统繁忙，请稍后再试");
        }

        return chain.filter(exchange);
    }

    /**
     * 尝试获取令牌（滑动窗口计数）
     * 使用Redis的INCR + EXPIRE实现简易令牌桶
     */
    private boolean tryAcquire(String key, int maxQps) {
        try {
            String currentSecond = String.valueOf(Instant.now().getEpochSecond());
            String windowKey = key + ":" + currentSecond;
            
            Long count = redisTemplate.opsForValue().increment(windowKey);
            if (count != null && count == 1) {
                redisTemplate.expire(windowKey, WINDOW_SECONDS + 1, TimeUnit.SECONDS);
            }
            
            return count == null || count <= maxQps;
        } catch (Exception e) {
            log.error("限流检查异常，放行请求: key={}", key, e);
            return true; // Redis异常时放行，不影响正常请求
        }
    }

    /**
     * 获取敏感接口的QPS限制
     */
    private Integer getSensitiveApiQps(String path) {
        // 精确匹配
        if (SENSITIVE_APIS.containsKey(path)) {
            return SENSITIVE_APIS.get(path);
        }
        // 前缀匹配（处理带路径参数的情况）
        for (java.util.Map.Entry<String, Integer> entry : SENSITIVE_APIS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // 多级代理取第一个
            int index = ip.indexOf(',');
            return index > 0 ? ip.substring(0, index).trim() : ip.trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddress() != null ? 
               request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }

    /**
     * 返回429 Too Many Requests响应
     */
    private Mono<Void> rateLimited(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(GLOBAL_QPS));
        response.getHeaders().add("Retry-After", "1");
        
        String body = "{\"code\":429,\"message\":\"" + message + "\",\"data\":null}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }

    @Override
    public int getOrder() {
        return -90; // 在AuthFilter(-100)之后执行
    }
}
