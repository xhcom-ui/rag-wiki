package com.zhiwiki.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * 网关访问日志过滤器
 * 记录每个请求的方法、路径、状态码、耗时、IP
 */
@Slf4j
@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = Instant.now().toEpochMilli();

        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getURI().getPath();
        String clientIp = getClientIp(request);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = Instant.now().toEpochMilli() - startTime;
            int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

            // 正常请求用info，慢请求用warn，错误用error
            if (statusCode >= 500) {
                log.error("[GW] {} {} -> {} {}ms ip={}", method, path, statusCode, duration, clientIp);
            } else if (duration > 3000) {
                log.warn("[GW-SLOW] {} {} -> {} {}ms ip={}", method, path, statusCode, duration, clientIp);
            } else if (log.isDebugEnabled() || !HttpMethod.OPTIONS.name().equals(method)) {
                log.info("[GW] {} {} -> {} {}ms ip={}", method, path, statusCode, duration, clientIp);
            }
        }));
    }

    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Real-IP");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeaders().getFirst("X-Forwarded-For");
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddress() != null ? 
                    request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }
        return ip;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10; // 在认证过滤器之前
    }
}
