package com.zhiwiki.common.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 多租户SQL拦截器
 * 
 * 自动在SQL语句中追加 tenant_id 条件，实现元数据级别的租户隔离。
 * 
 * 工作原理：
 * 1. SELECT: 自动追加 WHERE tenant_id = ?
 * 2. INSERT: 自动填充 tenant_id 字段
 * 3. UPDATE: 自动追加 WHERE tenant_id = ?
 * 4. DELETE: 自动追加 WHERE tenant_id = ?
 * 
 * 忽略表配置：
 * - 系统公共表（如sys_config、sys_dict等）不需要租户隔离
 * - 通过ignoredTables配置忽略的表
 */
public class RagWikiTenantHandler implements TenantLineHandler {

    /**
     * 不需要租户隔离的表（系统公共表）
     */
    private static final Set<String> IGNORED_TABLES = new HashSet<>(Arrays.asList(
            "sys_config",
            "sys_dict",
            "sys_dict_item",
            "sys_menu",
            "sys_notification",
            "llm_config",
            "ai_embedding_config",
            "security_alert_rule",
            "xxl_job_info",
            "xxl_job_log",
            "xxl_job_group",
            "xxl_job_registry"
    ));

    /**
     * 获取租户ID值
     * 从TenantContextHolder中获取当前租户ID
     */
    @Override
    public Expression getTenantId() {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            tenantId = "default";
        }
        return new StringValue(tenantId);
    }

    /**
     * 获取租户字段名
     */
    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    /**
     * 判断表是否需要租户隔离
     * 
     * 返回true表示忽略该表（不追加租户条件）
     * 返回false表示需要租户隔离（追加租户条件）
     */
    @Override
    public boolean ignoreTable(String tableName) {
        // 1. 系统公共表忽略
        if (IGNORED_TABLES.contains(tableName.toLowerCase())) {
            return true;
        }

        // 2. 集合隔离模式下，使用独立Schema，不需要SQL层面的租户过滤
        if (TenantContextHolder.isCollectionIsolation()) {
            return true;
        }

        // 3. 没有租户上下文时忽略（如系统内部调用）
        if (!TenantContextHolder.hasContext()) {
            return true;
        }

        // 4. 其他表都需要租户隔离
        return false;
    }
}
