package com.zhiwiki.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户API密钥实体
 * 用于个人API密钥管理，支持外部系统集成
 */
@Data
@TableName("user_api_key")
public class UserApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 密钥ID（唯一标识） */
    @TableField("key_id")
    private String keyId;

    /** 所属用户ID */
    @TableField("user_id")
    private String userId;

    /** 密钥名称/描述 */
    @TableField("key_name")
    private String keyName;

    /** API密钥值（sha256哈希存储） */
    @TableField("key_hash")
    private String keyHash;

    /** 密钥前缀（用于展示：sk-xxxx...xxxx） */
    @TableField("key_prefix")
    private String keyPrefix;

    /** 是否启用 */
    @TableField("is_enabled")
    private Integer isEnabled;

    /** 过期时间 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;

    /** 最后使用时间 */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;

    /** 速率限制（QPS） */
    @TableField("rate_limit")
    private Integer rateLimit;

    /** 允许的权限范围（JSON） */
    @TableField("allowed_scopes")
    private String allowedScopes;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
