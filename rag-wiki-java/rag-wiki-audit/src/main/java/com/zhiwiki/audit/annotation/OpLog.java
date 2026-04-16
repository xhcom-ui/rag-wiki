package com.zhiwiki.audit.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * 
 * 用于标记需要记录操作日志的方法，通过AOP切面自动记录用户操作行为。
 * 支持在Controller方法上使用，自动捕获请求参数、返回结果、执行时间等信息。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {

    /**
     * 操作模块
     */
    String module() default "";

    /**
     * 操作类型
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否保存请求参数
     */
    boolean saveParams() default true;

    /**
     * 是否保存返回结果
     */
    boolean saveResult() default false;

    /**
     * 排除的参数名称（敏感信息过滤）
     */
    String[] excludeParams() default {"password", "oldPassword", "newPassword", "token"};

    /**
     * 操作类型枚举
     */
    enum OperationType {
        /** 新增 */
        INSERT("新增"),
        /** 修改 */
        UPDATE("修改"),
        /** 删除 */
        DELETE("删除"),
        /** 查询 */
        SELECT("查询"),
        /** 导出 */
        EXPORT("导出"),
        /** 导入 */
        IMPORT("导入"),
        /** 登录 */
        LOGIN("登录"),
        /** 登出 */
        LOGOUT("登出"),
        /** 审批 */
        APPROVAL("审批"),
        /** 上传 */
        UPLOAD("上传"),
        /** 下载 */
        DOWNLOAD("下载"),
        /** AI调用 */
        AI_CALL("AI调用"),
        /** 其他 */
        OTHER("其他");

        private final String description;

        OperationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
