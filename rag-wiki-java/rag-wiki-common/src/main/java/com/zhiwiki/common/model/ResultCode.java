package com.zhiwiki.common.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应状态码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // ========== 通用 ==========
    SUCCESS(200, "操作成功"),
    INTERNAL_ERROR(500, "系统内部错误"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "未认证，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "请求资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // ========== 认证相关 1xxx ==========
    AUTH_LOGIN_FAILED(1001, "用户名或密码错误"),
    AUTH_TOKEN_EXPIRED(1002, "Token已过期"),
    AUTH_TOKEN_INVALID(1003, "Token无效"),
    AUTH_ACCOUNT_DISABLED(1004, "账号已被禁用"),
    AUTH_ACCOUNT_LOCKED(1005, "账号已被锁定"),
    AUTH_CAPTCHA_ERROR(1006, "验证码错误"),
    AUTH_PASSWORD_EXPIRED(1007, "密码已过期，请修改密码"),

    // ========== 权限相关 2xxx ==========
    PERM_DENIED(2001, "无操作权限"),
    PERM_DATA_DENIED(2002, "无数据访问权限"),
    PERM_SECURITY_LEVEL_DENIED(2003, "安全等级不足"),
    PERM_DEPT_DENIED(2004, "无部门数据权限"),
    PERM_ROLE_DENIED(2005, "无角色权限"),

    // ========== 用户相关 3xxx ==========
    USER_NOT_FOUND(3001, "用户不存在"),
    USER_ALREADY_EXISTS(3002, "用户已存在"),
    USER_PASSWORD_ERROR(3003, "密码错误"),

    // ========== 文档相关 4xxx ==========
    DOC_NOT_FOUND(4001, "文档不存在"),
    DOC_ALREADY_EXISTS(4002, "文档已存在"),
    DOC_SPACE_NOT_FOUND(4003, "知识库空间不存在"),
    DOC_UPLOAD_FAILED(4004, "文档上传失败"),
    DOC_PARSE_FAILED(4005, "文档解析失败"),
    DOC_PERMISSION_DENIED(4006, "文档访问权限不足"),

    // ========== 审批相关 5xxx ==========
    APPROVAL_NOT_FOUND(5001, "审批任务不存在"),
    APPROVAL_ALREADY_PROCESSED(5002, "审批任务已处理"),
    APPROVAL_DENIED(5003, "审批被拒绝"),

    // ========== AI相关 6xxx ==========
    AI_SERVICE_UNAVAILABLE(6001, "AI服务不可用"),
    AI_RAG_NO_RESULTS(6002, "未检索到授权范围内的相关内容"),
    AI_LLM_ERROR(6003, "LLM调用失败"),
    AI_CODE_SECURITY_RISK(6004, "代码存在安全风险"),
    AI_CODE_EXEC_TIMEOUT(6005, "代码执行超时"),
    AI_CODE_EXEC_ERROR(6006, "代码执行错误"),

    // ========== 审计相关 7xxx ==========
    AUDIT_LOG_NOT_FOUND(7001, "审计日志不存在"),
    BADCASE_NOT_FOUND(7002, "Badcase记录不存在");

    private final int code;
    private final String message;
}
