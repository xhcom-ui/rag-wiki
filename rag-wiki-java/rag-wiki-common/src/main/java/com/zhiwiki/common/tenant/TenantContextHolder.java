package com.zhiwiki.common.tenant;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.Data;

import java.io.Serializable;

/**
 * 租户上下文持有者
 * 
 * 基于TransmittableThreadLocal实现线程安全的租户上下文管理。
 * 支持异步线程池场景下的租户信息透传。
 * 
 * 使用方式：
 * 1. 网关层解析Token后设置：TenantContextHolder.set(tenantContext)
 * 2. 业务层获取：TenantContextHolder.getTenantId()
 * 3. 请求结束清理：TenantContextHolder.clear()
 */
public class TenantContextHolder {

    private static final TransmittableThreadLocal<TenantContext> CONTEXT = new TransmittableThreadLocal<>();

    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static String getTenantId() {
        TenantContext ctx = get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    public static TenantIsolationLevel getIsolationLevel() {
        TenantContext ctx = get();
        return ctx != null ? ctx.getIsolationLevel() : TenantIsolationLevel.METADATA_FILTER;
    }

    public static String getTenantName() {
        TenantContext ctx = get();
        return ctx != null ? ctx.getTenantName() : null;
    }

    public static boolean hasContext() {
        return get() != null;
    }

    /**
     * 判断当前租户是否使用集合隔离
     * 集合隔离需要独立的数据库Schema和向量Collection
     */
    public static boolean isCollectionIsolation() {
        return getIsolationLevel() == TenantIsolationLevel.COLLECTION;
    }

    /**
     * 判断当前租户是否使用分区隔离
     */
    public static boolean isPartitionIsolation() {
        return getIsolationLevel() == TenantIsolationLevel.PARTITION;
    }

    /**
     * 获取当前租户的数据库Schema名
     * 集合隔离模式下，每个租户使用独立Schema
     */
    public static String getSchemaName() {
        TenantContext ctx = get();
        if (ctx == null) {
            return null;
        }
        if (ctx.getIsolationLevel() == TenantIsolationLevel.COLLECTION) {
            return "tenant_" + ctx.getTenantId();
        }
        return null; // 非集合隔离使用默认Schema
    }

    /**
     * 获取当前租户的向量Collection前缀
     */
    public static String getVectorCollectionPrefix() {
        TenantContext ctx = get();
        if (ctx == null) {
            return "";
        }
        if (ctx.getIsolationLevel() == TenantIsolationLevel.COLLECTION) {
            return "tenant_" + ctx.getTenantId() + "_";
        }
        return "";
    }

    /**
     * 租户上下文信息
     */
    @Data
    public static class TenantContext implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 租户ID */
        private String tenantId;

        /** 租户名称 */
        private String tenantName;

        /** 隔离级别 */
        private TenantIsolationLevel isolationLevel;

        /** 租户状态：0-禁用 1-启用 */
        private Integer status;

        /** 存储配额(MB) */
        private Long storageQuotaMb;

        /** 已用存储(MB) */
        private Long storageUsedMb;

        /** 最大用户数 */
        private Integer maxUsers;

        /** 当前用户数 */
        private Integer currentUsers;

        /** 最大知识库数 */
        private Integer maxSpaces;

        /** 到期时间 */
        private String expireTime;

        /** 扩展配置 */
        private String extraConfig;
    }
}
