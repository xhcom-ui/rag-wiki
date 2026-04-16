package com.zhiwiki.approval.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审批提交请求
 */
@Data
@Schema(description = "审批提交请求")
public class ApprovalSubmitRequest {

    @Schema(description = "文档ID", required = true)
    @NotBlank(message = "文档ID不能为空")
    private String documentId;

    @Schema(description = "知识库空间ID", required = true)
    @NotBlank(message = "知识库空间ID不能为空")
    private String spaceId;

    @Schema(description = "安全等级：1-公开 2-内部 3-敏感 4-机密", required = true)
    @NotNull(message = "安全等级不能为空")
    @Min(value = 1, message = "安全等级最小为1")
    @Max(value = 4, message = "安全等级最大为4")
    private Integer securityLevel;

    @Schema(description = "提交者ID")
    private String submitterId;

    @Schema(description = "文档名称")
    @Size(max = 200, message = "文档名称不能超过200字")
    private String documentName;

    @Schema(description = "备注说明")
    @Size(max = 500, message = "备注不能超过500字")
    private String remark;
}
