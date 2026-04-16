package com.zhiwiki.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 安全上下文持有者 - 基于TransmittableThreadLocal
 * 
 * 使用阿里开源的 TransmittableThreadLocal (TTL) 替代普通 ThreadLocal，
 * 解决线程池中异步任务执行时上下文值传递问题。
 * 
 * TTL 优势：
 * 1. 支持线程池中值传递，子线程可以获取父线程的上下文
 * 2. 支持 @Async、CompletableFuture 等异步场景
 * 3. API 与 ThreadLocal 完全兼容
 * 
 * 使用场景：
 * - 异步任务执行时保持用户权限上下文
 * - 线程池处理请求时传递安全等级、租户ID等信息
 * - 跨线程的审计日志记录
 */
public class SecurityContextHolder {

    /**
     * 使用 TransmittableThreadLocal 替代普通 ThreadLocal
     * 确保在线程池、@Async 等异步场景下上下文正确传递
     */
    private static final TransmittableThreadLocal<UserPermissionContext> CONTEXT = new TransmittableThreadLocal<>();

    /**
     * 设置当前线程的安全上下文
     * @param context 用户权限上下文
     */
    public static void set(UserPermissionContext context) {
        CONTEXT.set(context);
    }

    /**
     * 获取当前线程的安全上下文
     * @return 用户权限上下文，未设置时返回 null
     */
    public static UserPermissionContext get() {
        return CONTEXT.get();
    }

    /**
     * 清除当前线程的安全上下文
     * 建议在请求结束时调用，防止内存泄漏
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 获取当前用户ID
     * @return 用户ID，未设置时返回 null
     */
    public static String getUserId() {
        UserPermissionContext ctx = get();
        return ctx != null ? ctx.getUserId() : null;
    }

    /**
     * 获取当前用户部门ID
     * @return 部门ID，未设置时返回 null
     */
    public static String getDeptId() {
        UserPermissionContext ctx = get();
        return ctx != null ? ctx.getDeptId() : null;
    }

    /**
     * 获取当前用户安全等级
     * @return 安全等级 (1-公开 2-内部 3-敏感 4-机密)，未设置时返回 0
     */
    public static int getSecurityLevel() {
        UserPermissionContext ctx = get();
        return ctx != null ? ctx.getSecurityLevel() : 0;
    }

    /**
     * 获取当前租户ID
     * @return 租户ID，未设置时返回 null
     */
    public static String getTenantId() {
        UserPermissionContext ctx = get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    /**
     * 判断当前是否有用户上下文
     * @return true-已登录用户，false-匿名用户
     */
    public static boolean hasContext() {
        return get() != null;
    }

    /**
     * 获取当前用户名
     * @return 用户名，未设置时返回 null
     */
    public static String getUsername() {
        UserPermissionContext ctx = get();
        return ctx != null ? ctx.getUsername() : null;
    }

    /**
     * 获取当前用户真实姓名
     * @return 真实姓名，未设置时返回 null
     */
    public static String getRealName() {
        UserPermissionContext ctx = get();
        return ctx != null ? ctx.getRealName() : null;
    }

    /**
     * 判断当前用户是否激活状态
     * @return true-激活，false-未激活或无上下文
     */
    public static boolean isActive() {
        UserPermissionContext ctx = get();
        return ctx != null && ctx.isActive();
    }
}
