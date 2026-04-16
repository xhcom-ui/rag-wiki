package com.zhiwiki.common.audit;

import java.lang.annotation.*;

/**
 * 全链路审计注解
 * 
 * 标注在需要审计记录的方法上，AOP切面自动记录：
 * - 操作人、操作时间、IP地址
 * - 操作类型、操作对象
 * - 请求参数、返回结果
 * - 执行耗时、是否成功
 * - 租户ID、部门ID、安全等级
 * 
 * 使用示例：
 * @AuditLog(action = "CREATE_DOCUMENT", module = "document", description = "创建文档")
 * public Result createDocument(DocumentUploadRequest request) { ... }
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 操作类型：CREATE/UPDATE/DELETE/QUERY/LOGIN/LOGOUT/APPROVE/EXPORT/IMPORT等
     */
    String action();

    /**
     * 功能模块：document/space/user/auth/audit/system等
     */
    String module() default "";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否记录请求参数（默认true）
     */
    boolean logParams() default true;

    /**
     * 是否记录返回结果（默认false，敏感接口不记录）
     */
    boolean logResult() default false;

    /**
     * 操作安全等级：1-公开 2-内部 3-敏感 4-机密
     * 高等级操作的审计日志会加密存储
     */
    int securityLevel() default 1;

    /**
     * 是否为关键操作（关键操作会额外告警通知）
     */
    boolean critical() default false;
}
