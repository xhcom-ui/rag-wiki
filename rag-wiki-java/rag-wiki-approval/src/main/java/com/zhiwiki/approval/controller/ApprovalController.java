package com.zhiwiki.approval.controller;

import com.zhiwiki.approval.dto.ApprovalFlowDetailResponse;
import com.zhiwiki.approval.dto.ApprovalProcessRequest;
import com.zhiwiki.approval.dto.ApprovalSubmitRequest;
import com.zhiwiki.approval.entity.ApprovalFlow;
import com.zhiwiki.approval.entity.ApprovalTask;
import com.zhiwiki.approval.service.ApprovalService;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
@Tag(name = "审批管理", description = "审批提交、审批处理、审批查询")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/submit")
    @Operation(summary = "提交审批", description = "按安全等级自动路由审批流程")
    public Result<ApprovalFlow> submit(@Valid @RequestBody ApprovalSubmitRequest request) {
        ApprovalFlow flow = approvalService.submitApproval(
                request.getDocumentId(),
                request.getSpaceId(),
                request.getSecurityLevel(),
                request.getSubmitterId()
        );
        return Result.success(flow);
    }

    @PostMapping("/process")
    @Operation(summary = "处理审批任务", description = "审批人处理审批任务，通过或拒绝")
    public Result<ApprovalTask> process(@Valid @RequestBody ApprovalProcessRequest request) {
        ApprovalTask task = approvalService.processApprovalTask(
                request.getTaskId(),
                request.getApproverId(),
                request.getStatus(),
                request.getComment()
        );
        return Result.success(task);
    }

    @PostMapping("/cancel/{flowId}")
    @Operation(summary = "取消审批", description = "提交者取消审批流程")
    public Result<Boolean> cancel(
            @PathVariable @Parameter(description = "审批流程ID") String flowId,
            @RequestParam @Parameter(description = "操作人ID") String operatorId) {
        return Result.success(approvalService.cancelApproval(flowId, operatorId));
    }

    @GetMapping("/detail/{flowId}")
    @Operation(summary = "获取审批详情", description = "获取审批流程详情，包含所有审批任务")
    public Result<ApprovalFlowDetailResponse> getDetail(
            @PathVariable @Parameter(description = "审批流程ID") String flowId) {
        return Result.success(approvalService.getApprovalDetail(flowId));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询审批流程", description = "支持按状态、提交者、安全等级筛选")
    public Result<PageResult<ApprovalFlow>> page(
            PageRequest pageRequest,
            @RequestParam(required = false) @Parameter(description = "审批状态") String status,
            @RequestParam(required = false) @Parameter(description = "提交者ID") String submitterId,
            @RequestParam(required = false) @Parameter(description = "安全等级") Integer securityLevel) {
        return Result.success(approvalService.pageApprovalFlows(pageRequest, status, submitterId, securityLevel));
    }

    @GetMapping("/my-pending")
    @Operation(summary = "我的待办任务", description = "获取当前用户的待审批任务列表")
    public Result<PageResult<ApprovalTask>> myPendingTasks(
            PageRequest pageRequest,
            @RequestParam(required = false) @Parameter(description = "审批人ID") String approverId,
            @RequestParam(required = false) @Parameter(description = "审批人类型") String approverType) {
        return Result.success(approvalService.pageMyPendingTasks(pageRequest, approverId, approverType));
    }

    @GetMapping("/document/{documentId}/latest")
    @Operation(summary = "获取文档最新审批状态", description = "根据文档ID获取最新的审批流程状态")
    public Result<ApprovalFlow> getLatestByDocument(
            @PathVariable @Parameter(description = "文档ID") String documentId) {
        return Result.success(approvalService.getLatestApprovalByDocument(documentId));
    }

    @GetMapping("/count/pending")
    @Operation(summary = "统计待审批数量", description = "统计当前待审批任务数量")
    public Result<Map<String, Object>> countPending(
            @RequestParam(required = false) @Parameter(description = "审批人类型") String approverType) {
        Map<String, Object> result = new HashMap<>();
        result.put("total", approvalService.countPendingApprovals(approverType));
        return Result.success(result);
    }
}
