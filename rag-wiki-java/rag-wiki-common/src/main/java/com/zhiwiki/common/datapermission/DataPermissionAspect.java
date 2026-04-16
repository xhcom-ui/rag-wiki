package com.zhiwiki.common.datapermission;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据权限AOP切面
 * 
 * 在Controller方法执行前自动装配数据权限上下文，
 * 方法执行后自动清理，防止ThreadLocal泄漏。
 * 
 * 使用方式：
 * 1. 在Controller类或方法上添加 @DataPermission 注解
 * 2. 本切面自动在方法执行前调用 DataPermissionAutoConfigurer.autoConfigure()
 * 3. DataPermissionInterceptor 自动读取上下文并注入SQL过滤条件
 * 4. 方法执行后自动清理上下文
 * 
 * 执行顺序：在租户上下文设置之后（@Order值越大越后执行）
 */
@Slf4j
@Aspect
@Component
@Order(100) // 在TenantAspect之后执行
public class DataPermissionAspect {

    /**
     * 环绕通知：自动装配数据权限上下文
     * 拦截所有带有@DataPermission注解的Controller方法
     */
    @Around("@annotation(dataPermission) || @within(dataPermission)")
    public Object around(ProceedingJoinPoint joinPoint, DataPermission dataPermission) throws Throwable {
        // 1. 自动装配数据权限上下文
        if (dataPermission.enabled()) {
            DataPermissionAutoConfigurer.autoConfigure();
            log.debug("数据权限AOP: 已为 {} 自动装配权限上下文, type={}", 
                    joinPoint.getSignature().toShortString(), 
                    DataPermissionContext.getContext() != null ? 
                            DataPermissionContext.getContext().getPermissionType() : "null");
        }

        try {
            // 2. 执行业务方法
            return joinPoint.proceed();
        } finally {
            // 3. 清理数据权限上下文
            DataPermissionAutoConfigurer.cleanup();
        }
    }
}
