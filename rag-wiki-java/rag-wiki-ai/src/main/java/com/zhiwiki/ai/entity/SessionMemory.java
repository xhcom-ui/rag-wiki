package com.zhiwiki.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话短期记忆实体
 */
@Data
@TableName("session_memory")
public class SessionMemory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("message_role")
    private String messageRole; // user/assistant/system

    @TableField("content")
    private String content;

    @TableField("message_index")
    private Integer messageIndex;

    @TableField("metadata")
    private String metadata;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("created_at")
    private LocalDateTime createdAt;
}