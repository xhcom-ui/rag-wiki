package com.zhiwiki.audit.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_audit_log")
@Schema(description = "RAG审计日志")
public class RagAuditLog extends BaseEntity {

    @TableField("audit_id")
    private String auditId;

    @TableField("user_id")
    private String userId;

    @TableField("dept_id")
    private String deptId;

    @TableField("user_role")
    private String userRole;

    @TableField("security_level")
    private Integer securityLevel;

    @TableField("query")
    private String query;

    @TableField("retrieved_doc_ids")
    private String retrievedDocIds;

    @TableField("retrieved_security_levels")
    private String retrievedSecurityLevels;

    @TableField("retrieved_dept_ids")
    private String retrievedDeptIds;

    @TableField("session_id")
    private String sessionId;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("response_time")
    private Long responseTime;

    @TableField("is_success")
    private Integer isSuccess;
}
