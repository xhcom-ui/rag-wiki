package com.zhiwiki.common.datapermission;

import java.lang.annotation.*;

/**
 * 数据权限注解
 * 标注在Controller方法上，用于自动注入数据权限过滤条件
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /**
     * 数据权限类型
     */
    DataPermissionType value() default DataPermissionType.DEPT;

    /**
     * 是否启用数据权限
     */
    boolean enabled() default true;

    /**
     * 部门字段名（默认dept_id）
     */
    String deptField() default "dept_id";

    /**
     * 用户字段名（默认user_id）
     */
    String userField() default "user_id";

    /**
     * 安全等级字段名（默认security_level）
     */
    String securityLevelField() default "security_level";

    /**
     * 表别名（多表查询时使用）
     */
    String tableAlias() default "";

    /**
     * 自定义SQL片段，支持SpEL表达式
     * 例如: "${deptId} IN (SELECT dept_id FROM user_dept WHERE user_id = '${userId}')"
     */
    String customSql() default "";
}
