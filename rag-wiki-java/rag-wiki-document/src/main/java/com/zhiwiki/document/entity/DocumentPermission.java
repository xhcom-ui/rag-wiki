package com.zhiwiki.document.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("document_permission")
@Schema(description = "文档权限")
public class DocumentPermission extends BaseEntity {

    @TableField("document_id")
    private String documentId;

    @TableField("space_id")
    private String spaceId;

    @TableField("security_level")
    private Integer securityLevel;

    @TableField("owning_dept_id")
    private String owningDeptId;

    @TableField("allowed_dept_ids")
    private String allowedDeptIds;

    @TableField("allowed_role_ids")
    private String allowedRoleIds;

    @TableField("min_role_level")
    private Integer minRoleLevel;

    @TableField("allowed_user_ids")
    private String allowedUserIds;

    @TableField("creator_id")
    private String creatorId;

    @TableField("valid_from")
    private String validFrom;

    @TableField("valid_to")
    private String validTo;

    @TableField("requires_approval")
    private Integer requiresApproval;
}
