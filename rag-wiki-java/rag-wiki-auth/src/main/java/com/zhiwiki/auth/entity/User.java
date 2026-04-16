package com.zhiwiki.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@Schema(description = "系统用户")
public class User extends BaseEntity {

    @Schema(description = "用户唯一标识")
    @TableField("user_id")
    private String userId;

    @Schema(description = "用户名")
    @TableField("username")
    private String username;

    @Schema(description = "加密密码")
    @TableField("password")
    private String password;

    @Schema(description = "真实姓名")
    @TableField("real_name")
    private String realName;

    @Schema(description = "邮箱")
    @TableField("email")
    private String email;

    @Schema(description = "手机号")
    @TableField("phone")
    private String phone;

    @Schema(description = "所属部门ID")
    @TableField("dept_id")
    private Long deptId;

    @Schema(description = "状态 0-禁用 1-启用")
    @TableField("status")
    private Integer status;

    @Schema(description = "安全等级 1-公开 2-内部 3-敏感 4-机密")
    @TableField("security_level")
    private Integer securityLevel;

    @Schema(description = "租户ID")
    @TableField("tenant_id")
    private String tenantId;

    @Schema(description = "头像URL")
    @TableField("avatar")
    private String avatar;

    @Schema(description = "扩展信息(OAuth2绑定/2FA等)")
    @TableField("ext_info")
    private String extInfo;
}
