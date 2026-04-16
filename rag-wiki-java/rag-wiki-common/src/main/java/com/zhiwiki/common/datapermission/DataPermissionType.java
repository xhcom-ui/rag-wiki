package com.zhiwiki.common.datapermission;

/**
 * 数据权限类型枚举
 */
public enum DataPermissionType {

    /**
     * 全部数据权限（超级管理员）
     */
    ALL,

    /**
     * 本部门数据权限
     */
    DEPT,

    /**
     * 本部门及子部门数据权限
     */
    DEPT_AND_CHILD,

    /**
     * 仅本人数据权限
     */
    SELF,

    /**
     * 自定义数据权限
     */
    CUSTOM,

    /**
     * 按安全等级过滤
     */
    SECURITY_LEVEL,

    /**
     * 混合权限（部门+安全等级）
     */
    MIXED
}
