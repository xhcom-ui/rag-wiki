package com.zhiwiki.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
@Schema(description = "部门")
public class Dept extends BaseEntity {

    @Schema(description = "部门唯一标识")
    @TableField("dept_id")
    private String deptId;

    @Schema(description = "部门名称")
    @TableField("dept_name")
    private String deptName;

    @Schema(description = "父部门ID")
    @TableField("parent_id")
    private Long parentId;

    @Schema(description = "排序")
    @TableField("sort")
    private Integer sort;

    @Schema(description = "状态 0-禁用 1-启用")
    @TableField("status")
    private Integer status;

    @Schema(description = "租户ID")
    @TableField("tenant_id")
    private String tenantId;
}
