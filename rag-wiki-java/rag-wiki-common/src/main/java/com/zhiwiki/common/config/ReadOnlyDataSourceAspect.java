package com.zhiwiki.common.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 读写分离AOP切面
 * 
 * 自动根据Service方法名判断读写操作：
 * - 以 select/get/query/find/list/count/page 开头 → 从库(SLAVE)
 * - 其他（insert/update/delete/save/remove） → 主库(MASTER)
 * 
 * 也支持手动指定：
 *   DynamicDataSourceContextHolder.setSlave();
 *   // ... 查询操作
 *   DynamicDataSourceContextHolder.clear();
 * 
 * 切面优先级最高（Ordered.HIGHEST_PRECEDENCE + 1），
 * 确保在@Transactional之前执行数据源切换。
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(prefix = "spring.datasource.read-write-separation", name = "enabled", havingValue = "true")
public class ReadOnlyDataSourceAspect implements Ordered {

    /** 只读方法前缀 - 匹配这些前缀的方法自动路由到从库 */
    private static final String[] READ_PREFIXES = {
        "select", "get", "query", "find", "list", "count", "page", "search", "check", "is", "has"
    };

    @Around("@within(org.springframework.stereotype.Service) || @within(org.springframework.stereotype.Repository)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        
        // 判断是否为读操作
        boolean isRead = isReadMethod(methodName);
        
        try {
            if (isRead) {
                DynamicDataSourceContextHolder.setSlave();
            } else {
                DynamicDataSourceContextHolder.setMaster();
            }
            return joinPoint.proceed();
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }

    /**
     * 判断方法是否为读操作
     */
    private boolean isReadMethod(String methodName) {
        String lowerName = methodName.toLowerCase();
        for (String prefix : READ_PREFIXES) {
            if (lowerName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // 在@Transactional之前执行
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
