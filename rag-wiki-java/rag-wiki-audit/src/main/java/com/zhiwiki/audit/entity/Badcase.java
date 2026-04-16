package com.zhiwiki.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG Badcase实体
 */
@Data
@TableName("rag_badcase")
public class Badcase {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("case_id")
    private String caseId;

    @TableField("query")
    private String query;

    @TableField("answer")
    private String answer;

    @TableField("expected_answer")
    private String expectedAnswer;

    @TableField("badcase_type")
    private String badcaseType;

    @TableField("source")
    private String source;

    @TableField("user_id")
    private String userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("confidence_score")
    private Float confidenceScore;

    @TableField("status")
    private String status; // NEW/IN_REVIEW/FIXED/WONT_FIX

    @TableField("assigned_to")
    private String assignedTo;

    @TableField("fix_strategy")
    private String fixStrategy;

    @TableField("resolved_at")
    private LocalDateTime resolvedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}