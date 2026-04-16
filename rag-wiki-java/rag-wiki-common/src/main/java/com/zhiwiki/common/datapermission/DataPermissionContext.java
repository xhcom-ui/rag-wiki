package com.zhiwiki.common.datapermission;

import lombok.Data;

import java.util.List;

/**
 * 数据权限上下文
 * 用于存储当前用户的数据权限信息
 */
@Data
public class DataPermissionContext {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 部门ID
     */
    private String deptId;

    /**
     * 子部门ID列表（包含本部门）
     */
    private List<String> deptIdList;

    /**
     * 用户安全等级
     */
    private Integer securityLevel;

    /**
     * 角色列表
     */
    private List<String> roleIds;

    /**
     * 是否超级管理员
     */
    private boolean superAdmin;

    /**
     * 数据权限类型
     */
    private DataPermissionType permissionType;

    /**
     * 自定义权限SQL
     */
    private String customSql;

    /**
     * 构建SQL过滤条件
     */
    public String buildFilterSql(String tableAlias, String deptField, String userField, 
                                  String securityLevelField) {
        if (superAdmin) {
            return null; // 超级管理员不过滤
        }

        StringBuilder sql = new StringBuilder();
        String prefix = tableAlias.isEmpty() ? "" : tableAlias + ".";

        switch (permissionType) {
            case ALL:
                return null;
            case DEPT:
                sql.append(prefix).append(deptField).append(" = '").append(deptId).append("'");
                break;
            case DEPT_AND_CHILD:
                if (deptIdList != null && !deptIdList.isEmpty()) {
                    sql.append(prefix).append(deptField).append(" IN (");
                    sql.append(String.join(",", deptIdList.stream()
                            .map(id -> "'" + id + "'")
                            .toArray(String[]::new)));
                    sql.append(")");
                }
                break;
            case SELF:
                sql.append(prefix).append(userField).append(" = '").append(userId).append("'");
                break;
            case SECURITY_LEVEL:
                if (securityLevel != null) {
                    sql.append(prefix).append(securityLevelField).append(" <= ").append(securityLevel);
                }
                break;
            case MIXED:
                // 混合权限：部门 + 安全等级
                buildMixedSql(sql, prefix, deptField, securityLevelField);
                break;
            case CUSTOM:
                if (customSql != null && !customSql.isEmpty()) {
                    sql.append(customSql);
                }
                break;
            default:
                break;
        }

        return sql.length() > 0 ? sql.toString() : null;
    }

    private void buildMixedSql(StringBuilder sql, String prefix, String deptField, 
                               String securityLevelField) {
        sql.append("(");
        
        // 部门过滤
        if (deptIdList != null && !deptIdList.isEmpty()) {
            sql.append(prefix).append(deptField).append(" IN (");
            sql.append(String.join(",", deptIdList.stream()
                    .map(id -> "'" + id + "'")
                    .toArray(String[]::new)));
            sql.append(")");
        } else {
            sql.append(prefix).append(deptField).append(" = '").append(deptId).append("'");
        }
        
        // 安全等级过滤
        if (securityLevel != null) {
            sql.append(" AND ").append(prefix).append(securityLevelField)
                    .append(" <= ").append(securityLevel);
        }
        
        sql.append(")");
    }

    /**
     * ThreadLocal 存储上下文
     */
    private static final ThreadLocal<DataPermissionContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(DataPermissionContext context) {
        CONTEXT.set(context);
    }

    public static DataPermissionContext getContext() {
        return CONTEXT.get();
    }

    public static void clearContext() {
        CONTEXT.remove();
    }
}
