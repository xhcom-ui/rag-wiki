package com.zhiwiki.approval.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_task")
@Schema(description = "审批任务")
public class ApprovalTask extends BaseEntity {

    @TableField("task_id")
    private String taskId;

    @TableField("flow_id")
    private String flowId;

    @TableField("step")
    private Integer step;

    @TableField("approver_id")
    private String approverId;

    @TableField("approver_type")
    private String approverType; // DEPT_HEAD / COMPLIANCE / ADMIN

    @TableField("status")
    private String status;

    @TableField("comment")
    private String comment;

    @TableField("approved_at")
    private String approvedAt;
}
