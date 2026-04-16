package com.zhiwiki.approval.dto;

import com.zhiwiki.approval.entity.ApprovalTask;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批流程详情响应
 */
@Data
@Schema(description = "审批流程详情")
public class ApprovalFlowDetailResponse {

    @Schema(description = "审批流程ID")
    private String flowId;

    @Schema(description = "文档ID")
    private String documentId;

    @Schema(description = "知识库空间ID")
    private String spaceId;

    @Schema(description = "安全等级")
    private Integer securityLevel;

    @Schema(description = "安全等级名称")
    private String securityLevelName;

    @Schema(description = "提交者ID")
    private String submitterId;

    @Schema(description = "提交者名称")
    private String submitterName;

    @Schema(description = "当前步骤")
    private Integer currentStep;

    @Schema(description = "总步骤数")
    private Integer totalSteps;

    @Schema(description = "审批状态")
    private String status;

    @Schema(description = "审批结果")
    private String result;

    @Schema(description = "文档名称")
    private String documentName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "审批任务列表")
    private List<TaskDetail> tasks;

    /**
     * 任务详情
     */
    @Data
    @Schema(description = "审批任务详情")
    public static class TaskDetail {
        @Schema(description = "任务ID")
        private String taskId;

        @Schema(description = "步骤序号")
        private Integer step;

        @Schema(description = "审批人ID")
        private String approverId;

        @Schema(description = "审批人名称")
        private String approverName;

        @Schema(description = "审批人类型")
        private String approverType;

        @Schema(description = "审批人类型名称")
        private String approverTypeName;

        @Schema(description = "状态")
        private String status;

        @Schema(description = "审批意见")
        private String comment;

        @Schema(description = "审批时间")
        private String approvedAt;

        public static TaskDetail fromEntity(ApprovalTask task) {
            TaskDetail detail = new TaskDetail();
            detail.setTaskId(task.getTaskId());
            detail.setStep(task.getStep());
            detail.setApproverId(task.getApproverId());
            detail.setApproverType(task.getApproverType());
            detail.setApproverTypeName(getApproverTypeName(task.getApproverType()));
            detail.setStatus(task.getStatus());
            detail.setComment(task.getComment());
            detail.setApprovedAt(task.getApprovedAt());
            return detail;
        }

        private static String getApproverTypeName(String type) {
            if (type == null) return "";
            switch (type) {
                case "DEPT_HEAD": return "部门负责人";
                case "COMPLIANCE": return "合规部门";
                case "ADMIN": return "管理员";
                default: return type;
            }
        }
    }

    /**
     * 获取安全等级名称
     */
    public String getSecurityLevelName() {
        if (securityLevel == null) return "";
        switch (securityLevel) {
            case 1: return "公开";
            case 2: return "内部";
            case 3: return "敏感";
            case 4: return "机密";
            default: return "未知";
        }
    }
}
