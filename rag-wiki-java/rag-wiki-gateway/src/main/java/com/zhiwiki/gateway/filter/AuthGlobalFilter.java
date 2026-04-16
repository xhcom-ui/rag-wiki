package com.zhiwiki.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 全局认证过滤器 - 校验Sa-Token登录状态 + 权限版本校验 + 用户状态检查
 * 
 * 安全防线：
 * 1. 白名单路径放行
 * 2. Sa-Token登录状态校验
 * 3. Token黑名单校验
 * 4. 用户禁用状态校验（Redis实时标记）
 * 5. 权限版本号校验（权限变更后强制重新登录）
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final StringRedisTemplate redisTemplate;

    public AuthGlobalFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 白名单路径 - 不需要认证 */
    private static final String[] WHITE_LIST = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/captcha",
            "/api/auth/oauth2/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/favicon.ico"
    };

    /** 权限相关常量 */
    private static final String TOKEN_BLACKLIST_PREFIX = "rag-wiki:token:blacklist:";
    private static final String USER_STATUS_PREFIX = "rag-wiki:user:status:";
    private static final String PERMISSION_VERSION_PREFIX = "rag-wiki:permission:version:";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 白名单路径放行
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        try {
            // 1. 校验Sa-Token登录状态
            Object loginId = StpUtil.getLoginId();
            if (loginId == null) {
                return unauthorized(exchange, "未登录");
            }

            String userId = loginId.toString();
            String tokenValue = StpUtil.getTokenValue();

            // 2. Token黑名单校验
            if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + tokenValue))) {
                log.warn("Token已被加入黑名单: userId={}", userId);
                return unauthorized(exchange, "Token已失效，请重新登录");
            }

            // 3. 用户禁用状态校验
            if ("DISABLED".equals(redisTemplate.opsForValue().get(USER_STATUS_PREFIX + userId))) {
                log.warn("用户已被禁用: userId={}", userId);
                return unauthorized(exchange, "账户已被禁用");
            }

            // 4. 权限版本校验
            String versionStr = redisTemplate.opsForValue().get(PERMISSION_VERSION_PREFIX + userId);
            if (versionStr != null) {
                long currentVersion = Long.parseLong(versionStr);
                // 从Session获取Token签发时的版本号
                Object tokenVersionObj = StpUtil.getSession().get("permissionVersion");
                long tokenVersion = tokenVersionObj != null ? (Long) tokenVersionObj : 0;
                
                if (tokenVersion < currentVersion) {
                    log.warn("用户权限已变更，需要重新登录: userId={}, tokenVersion={}, currentVersion={}", 
                            userId, tokenVersion, currentVersion);
                    // 强制下线
                    StpUtil.logout(userId);
                    return unauthorized(exchange, "权限已变更，请重新登录");
                }
            }

            // 5. 将用户信息注入请求头传递给下游服务
            List<String> roleIds = (List<String>) StpUtil.getSession().get("roleIds");
            String roleIdsStr = roleIds != null ? String.join(",", roleIds) : "";

            ServerHttpRequest newRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Name", String.valueOf(StpUtil.getSession().get("username")))
                    .header("X-Dept-Id", String.valueOf(StpUtil.getSession().get("deptId")))
                    .header("X-Security-Level", String.valueOf(StpUtil.getSession().get("securityLevel")))
                    .header("X-Tenant-Id", String.valueOf(StpUtil.getSession().get("tenantId")))
                    .header("X-Role-Ids", roleIdsStr)
                    .build();
            return chain.filter(exchange.mutate().request(newRequest).build());

        } catch (Exception e) {
            log.warn("认证失败, path={}, error={}", path, e.getMessage());
        }

        return unauthorized(exchange, "认证失败");
    }

    /**
     * 返回401未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Auth-Error", message);
        return exchange.getResponse().setComplete();
    }

    private boolean isWhiteListed(String path) {
        for (String pattern : WHITE_LIST) {
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (path.startsWith(prefix)) {
                    return true;
                }
            } else if (path.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
