package com.zhiwiki.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批处理请求
 */
@Data
@Schema(description = "审批处理请求")
public class ApprovalProcessRequest {

    @Schema(description = "任务ID", required = true)
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @Schema(description = "审批人ID", required = true)
    @NotBlank(message = "审批人ID不能为空")
    private String approverId;

    @Schema(description = "审批状态：APPROVED/REJECTED", required = true)
    @NotBlank(message = "审批状态不能为空")
    @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "审批状态只能是APPROVED或REJECTED")
    private String status;

    @Schema(description = "审批意见")
    @Size(max = 500, message = "审批意见不能超过500字")
    private String comment;
}
