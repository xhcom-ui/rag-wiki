package com.zhiwiki.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户长期记忆实体
 */
@Data
@TableName("user_memory")
public class UserMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("memory_id")
    private String memoryId;

    @TableField("user_id")
    private String userId;

    @TableField("content")
    private String content;

    @TableField("memory_type")
    private String memoryType; // SEMANTIC/EPISODIC/PROCEDURAL

    @TableField("embedding")
    private String embedding;

    @TableField("metadata")
    private String metadata;

    @TableField("source_session_id")
    private String sourceSessionId;

    @TableField("confidence_score")
    private Float confidenceScore;

    @TableField("ttl")
    private Long ttl;

    @TableField("operation_type")
    private String operationType; // ADD/UPDATE/DELETE/NOOP

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}