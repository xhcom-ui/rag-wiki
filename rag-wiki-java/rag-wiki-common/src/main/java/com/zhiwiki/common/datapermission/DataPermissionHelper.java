package com.zhiwiki.common.datapermission;

import java.util.List;

/**
 * 数据权限助手类
 * 提供便捷的数据权限上下文设置方法
 */
public class DataPermissionHelper {

    /**
     * 设置超级管理员权限
     */
    public static void setSuperAdmin(String userId) {
        DataPermissionContext context = new DataPermissionContext();
        context.setUserId(userId);
        context.setSuperAdmin(true);
        context.setPermissionType(DataPermissionType.ALL);
        DataPermissionContext.setContext(context);
    }

    /**
     * 设置部门数据权限
     */
    public static void setDeptPermission(String userId, String deptId) {
        DataPermissionContext context = new DataPermissionContext();
        context.setUserId(userId);
        context.setDeptId(deptId);
        context.setPermissionType(DataPermissionType.DEPT);
        DataPermissionContext.setContext(context);
    }

    /**
     * 设置部门及子部门数据权限
     */
    public static void setDeptAndChildPermission(String userId, String deptId, 
                                                  List<String> deptIdList) {
        DataPermissionContext context = new DataPermissionContext();
        context.setUserId(userId);
        context.setDeptId(deptId);
        context.setDeptIdList(deptIdList);
        context.setPermissionType(DataPermissionType.DEPT_AND_CHILD);
        DataPermissionContext.setContext(context);
    }

    /**
     * 设置仅本人数据权限
     */
    public static void setSelfPermission(String userId) {
        DataPermissionContext context = new DataPermissionContext();
        context.setUserId(userId);
        context.setPermissionType(DataPermissionType.SELF);
        DataPermissionContext.setContext(context);
    }

    /**
     * 设置安全等级数据权限
     */
    public static void setSecurityLevelPermission(String userId, Integer securityLevel) {
        DataPermissionContext context = new DataPermissionContext();
        context.setUserId(userId);
        context.setSecurityLevel(securityLevel);
        context.setPermissionType(DataPermissionType.SECURITY_LEVEL);
        DataPermissionContext.setContext(context);
    }

    /**
     * 设置混合数据权限（部门 + 安全等级）
     */
    public static void setMixedPermission(String userId, String deptId, 
                                          List<String> deptIdList, Integer securityLevel) {
        DataPermissionContext context = new DataPermissionContext();
        context.setUserId(userId);
        context.setDeptId(deptId);
        context.setDeptIdList(deptIdList);
        context.setSecurityLevel(securityLevel);
        context.setPermissionType(DataPermissionType.MIXED);
        DataPermissionContext.setContext(context);
    }

    /**
     * 设置自定义数据权限
     */
    public static void setCustomPermission(String userId, String customSql) {
        DataPermissionContext context = new DataPermissionContext();
        context.setUserId(userId);
        context.setCustomSql(customSql);
        context.setPermissionType(DataPermissionType.CUSTOM);
        DataPermissionContext.setContext(context);
    }

    /**
     * 清除数据权限上下文
     */
    public static void clear() {
        DataPermissionContext.clearContext();
    }

    /**
     * 获取当前用户ID
     */
    public static String getCurrentUserId() {
        DataPermissionContext context = DataPermissionContext.getContext();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 判断当前用户是否为超级管理员
     */
    public static boolean isSuperAdmin() {
        DataPermissionContext context = DataPermissionContext.getContext();
        return context != null && context.isSuperAdmin();
    }
}
