package com.zhiwiki.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全告警实体
 */
@Data
@TableName("security_alert")
public class SecurityAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("alert_id")
    private String alertId;

    @TableField("rule_id")
    private String ruleId;

    @TableField("alert_type")
    private String alertType;

    @TableField("severity")
    private String severity; // LOW/MEDIUM/HIGH/CRITICAL

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("source_ip")
    private String sourceIp;

    @TableField("user_id")
    private String userId;

    @TableField("target_id")
    private String targetId;

    @TableField("evidence")
    private String evidence;

    @TableField("status")
    private String status; // NEW/CONFIRMED/HANDLING/RESOLVED/IGNORED

    @TableField("handler_id")
    private String handlerId;

    @TableField("handle_result")
    private String handleResult;

    @TableField("handled_at")
    private LocalDateTime handledAt;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}