package com.zhiwiki.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
@Schema(description = "角色")
public class Role extends BaseEntity {

    @Schema(description = "角色唯一标识")
    @TableField("role_id")
    private String roleId;

    @Schema(description = "角色名称")
    @TableField("role_name")
    private String roleName;

    @Schema(description = "角色标识")
    @TableField("role_code")
    private String roleCode;

    @Schema(description = "角色等级")
    @TableField("role_level")
    private Integer roleLevel;

    @Schema(description = "描述")
    @TableField("description")
    private String description;

    @Schema(description = "状态 0-禁用 1-启用")
    @TableField("status")
    private Integer status;

    @Schema(description = "租户ID")
    @TableField("tenant_id")
    private String tenantId;
}
