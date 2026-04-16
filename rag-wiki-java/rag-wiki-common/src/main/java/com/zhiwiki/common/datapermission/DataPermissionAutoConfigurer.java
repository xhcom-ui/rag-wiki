package com.zhiwiki.common.datapermission;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.zhiwiki.common.context.SecurityContextHolder;
import com.zhiwiki.common.context.UserPermissionContext;
import com.zhiwiki.common.tenant.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * 增强型数据权限自动装配器
 * 
 * 自动从SecurityContextHolder和TenantContextHolder构建数据权限上下文，
 * 实现三层RLS（Row Level Security）：
 * 
 * 1. 租户层：tenant_id过滤（强制）
 * 2. 安全等级层：security_level过滤
 * 3. 部门/角色层：dept_id + role_id过滤
 * 
 * 使用方式：
 * - 在Controller层或AOP切面中调用 autoConfigure()
 * - 数据权限拦截器会自动读取上下文并注入SQL过滤条件
 */
@Slf4j
public class DataPermissionAutoConfigurer {

    /**
     * 使用TransmittableThreadLocal确保异步线程也能获取到数据权限上下文
     */
    private static final TransmittableThreadLocal<Boolean> AUTO_CONFIGURED = new TransmittableThreadLocal<>();

    /**
     * 自动装配数据权限上下文
     * 根据当前登录用户的权限信息，自动构建并设置DataPermissionContext
     */
    public static void autoConfigure() {
        UserPermissionContext userCtx = SecurityContextHolder.get();
        if (userCtx == null) {
            log.debug("无用户上下文，跳过数据权限自动装配");
            return;
        }

        String tenantId = TenantContextHolder.getTenantId();
        DataPermissionContext dpCtx = new DataPermissionContext();
        dpCtx.setUserId(userCtx.getUserId());
        dpCtx.setDeptId(userCtx.getDeptId());
        dpCtx.setSecurityLevel(userCtx.getSecurityLevel());
        dpCtx.setRoleIds(userCtx.getRoleIds());

        // 判断是否超级管理员
        boolean isSuperAdmin = userCtx.getRoleCodes() != null && 
                (userCtx.getRoleCodes().contains("super_admin") || userCtx.getRoleCodes().contains("admin"));
        dpCtx.setSuperAdmin(isSuperAdmin);

        // 根据角色等级决定数据权限范围
        if (isSuperAdmin) {
            dpCtx.setPermissionType(DataPermissionType.ALL);
        } else if (userCtx.getMaxRoleLevel() >= 5) {
            // 高级管理层：本部门及子部门 + 安全等级
            dpCtx.setPermissionType(DataPermissionType.MIXED);
        } else if (userCtx.getMaxRoleLevel() >= 3) {
            // 中层管理：本部门 + 安全等级
            dpCtx.setDeptIdList(java.util.Collections.singletonList(userCtx.getDeptId()));
            dpCtx.setPermissionType(DataPermissionType.MIXED);
        } else {
            // 普通员工：仅本人 + 安全等级
            dpCtx.setPermissionType(DataPermissionType.SELF);
        }

        DataPermissionContext.setContext(dpCtx);
        AUTO_CONFIGURED.set(true);
        log.debug("数据权限自动装配完成: userId={}, type={}, tenantId={}", 
                userCtx.getUserId(), dpCtx.getPermissionType(), tenantId);
    }

    /**
     * 清除自动装配的数据权限上下文
     */
    public static void cleanup() {
        if (Boolean.TRUE.equals(AUTO_CONFIGURED.get())) {
            DataPermissionContext.clearContext();
            AUTO_CONFIGURED.remove();
        }
    }

    /**
     * 是否已自动装配
     */
    public static boolean isAutoConfigured() {
        return Boolean.TRUE.equals(AUTO_CONFIGURED.get());
    }
}
