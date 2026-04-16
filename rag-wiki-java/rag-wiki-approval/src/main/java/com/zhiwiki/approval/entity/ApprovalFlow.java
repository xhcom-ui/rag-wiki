package com.zhiwiki.approval.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhiwiki.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("approval_flow")
@Schema(description = "审批流程")
public class ApprovalFlow extends BaseEntity {

    @TableField("flow_id")
    private String flowId;

    @TableField("document_id")
    private String documentId;

    @TableField("space_id")
    private String spaceId;

    @TableField("security_level")
    private Integer securityLevel;

    @TableField("submitter_id")
    private String submitterId;

    @TableField("current_step")
    private Integer currentStep;

    @TableField("total_steps")
    private Integer totalSteps;

    @TableField("status")
    private String status;

    @TableField("result")
    private String result;
}
