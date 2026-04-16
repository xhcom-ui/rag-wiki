package com.zhiwiki.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
@Schema(description = "租户信息")
public class Tenant extends BaseEntity {

    @Schema(description = "租户ID")
    @TableField("tenant_id")
    private String tenantId;

    @Schema(description = "租户名称")
    @TableField("tenant_name")
    private String tenantName;

    @Schema(description = "租户编码（唯一标识）")
    @TableField("tenant_code")
    private String tenantCode;

    @Schema(description = "联系人")
    @TableField("contact_name")
    private String contactName;

    @Schema(description = "联系电话")
    @TableField("contact_phone")
    private String contactPhone;

    @Schema(description = "联系邮箱")
    @TableField("contact_email")
    private String contactEmail;

    @Schema(description = "隔离级别: metadata_filter/partition/collection")
    @TableField("isolation_level")
    private String isolationLevel;

    @Schema(description = "状态：0-禁用 1-启用 2-试用")
    @TableField("status")
    private Integer status;

    @Schema(description = "存储配额(MB)")
    @TableField("storage_quota_mb")
    private Long storageQuotaMb;

    @Schema(description = "已用存储(MB)")
    @TableField("storage_used_mb")
    private Long storageUsedMb;

    @Schema(description = "最大用户数")
    @TableField("max_users")
    private Integer maxUsers;

    @Schema(description = "最大知识库数")
    @TableField("max_spaces")
    private Integer maxSpaces;

    @Schema(description = "到期时间")
    @TableField("expire_time")
    private String expireTime;

    @Schema(description = "域名绑定")
    @TableField("domain")
    private String domain;

    @Schema(description = "Logo URL")
    @TableField("logo_url")
    private String logoUrl;

    @Schema(description = "扩展配置JSON")
    @TableField("extra_config")
    private String extraConfig;
}
