package com.zhiwiki.common.constant;

/**
 * 系统常量
 */
public final class SystemConstants {

    private SystemConstants() {}

    // ========== 安全等级 ==========
    /** 公开 */
    public static final int SECURITY_LEVEL_PUBLIC = 1;
    /** 内部 */
    public static final int SECURITY_LEVEL_INTERNAL = 2;
    /** 敏感 */
    public static final int SECURITY_LEVEL_SENSITIVE = 3;
    /** 机密 */
    public static final int SECURITY_LEVEL_CONFIDENTIAL = 4;

    // ========== 文档状态 ==========
    /** 草稿 */
    public static final int DOC_STATUS_DRAFT = 0;
    /** 已发布 */
    public static final int DOC_STATUS_PUBLISHED = 1;
    /** 已归档 */
    public static final int DOC_STATUS_ARCHIVED = 2;

    // ========== 知识库类型 ==========
    /** 私有 */
    public static final int SPACE_TYPE_PRIVATE = 1;
    /** 部门 */
    public static final int SPACE_TYPE_DEPT = 2;
    /** 公共 */
    public static final int SPACE_TYPE_PUBLIC = 3;

    // ========== 用户状态 ==========
    /** 禁用 */
    public static final int USER_STATUS_DISABLED = 0;
    /** 启用 */
    public static final int USER_STATUS_ENABLED = 1;

    // ========== 审批状态 ==========
    public static final String APPROVAL_STATUS_PENDING = "PENDING";
    public static final String APPROVAL_STATUS_APPROVED = "APPROVED";
    public static final String APPROVAL_STATUS_REJECTED = "REJECTED";
    public static final String APPROVAL_STATUS_CANCELLED = "CANCELLED";

    // ========== 解析任务状态 ==========
    public static final String PARSE_STATUS_PENDING = "PENDING";
    public static final String PARSE_STATUS_PARSING = "PARSING";
    public static final String PARSE_STATUS_VECTORIZING = "VECTORIZING";
    public static final String PARSE_STATUS_COMPLETED = "COMPLETED";
    public static final String PARSE_STATUS_FAILED = "FAILED";
    public static final String PARSE_STATUS_NEED_REVIEW = "NEED_REVIEW";

    // ========== Badcase类型 ==========
    public static final String BADCASE_RETRIEVAL_FAILURE = "RETRIEVAL_FAILURE";
    public static final String BADCASE_HALLUCINATION = "HALLUCINATION";
    public static final String BADCASE_ROUTING_ERROR = "ROUTING_ERROR";
    public static final String BADCASE_KNOWLEDGE_GAP = "KNOWLEDGE_GAP";

    // ========== 记忆类型 ==========
    public static final String MEMORY_TYPE_SEMANTIC = "SEMANTIC";
    public static final String MEMORY_TYPE_EPISODIC = "EPISODIC";
    public static final String MEMORY_TYPE_PROCEDURAL = "PROCEDURAL";

    // ========== Redis Key 前缀 ==========
    public static final String REDIS_PERMISSION_PREFIX = "user:permissions:";
    public static final String REDIS_SESSION_PREFIX = "user:session:";
    public static final String REDIS_PERMISSION_CACHE_TTL = "300"; // 5 minutes

    // ========== MQ Exchange/Queue ==========
    public static final String MQ_DOCUMENT_EXCHANGE = "wiki.document.exchange";
    public static final String MQ_DOCUMENT_PARSE_QUEUE = "wiki.document.parse.queue";
    public static final String MQ_DOCUMENT_VECTOR_QUEUE = "wiki.document.vector.queue";
    public static final String MQ_DOCUMENT_DLQ = "wiki.document.dlq";
    public static final String MQ_AI_EXCHANGE = "wiki.ai.exchange";
    public static final String MQ_AI_MEMORY_QUEUE = "wiki.ai.memory.queue";
    public static final String MQ_AI_BADCASE_QUEUE = "wiki.ai.badcase.queue";

    // ========== 超级管理员角色 ==========
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
}
