package com.zhiwiki.approval.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiwiki.approval.dto.ApprovalFlowDetailResponse;
import com.zhiwiki.approval.entity.ApprovalFlow;
import com.zhiwiki.approval.entity.ApprovalTask;
import com.zhiwiki.approval.mapper.ApprovalFlowMapper;
import com.zhiwiki.approval.mapper.ApprovalTaskMapper;
import com.zhiwiki.common.constant.SystemConstants;
import com.zhiwiki.common.exception.BusinessException;
import com.zhiwiki.common.model.PageRequest;
import com.zhiwiki.common.model.PageResult;
import com.zhiwiki.common.model.ResultCode;
import com.zhiwiki.approval.event.ApprovalCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批服务 - 分级审批路由
 * 
 * 按安全等级分级审批：
 * - 公开(1): 自动审批通过
 * - 内部(2): 部门负责人审批
 * - 敏感(3): 部门负责人 + 合规部门双人审批
 * - 机密(4): 多级管理层审批
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalFlowMapper flowMapper;
    private final ApprovalTaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 提交审批 - 按安全等级路由审批流程
     */
    @Transactional
    public ApprovalFlow submitApproval(String documentId, String spaceId, Integer securityLevel, String submitterId) {
        // 检查是否已有进行中的审批
        LambdaQueryWrapper<ApprovalFlow> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(ApprovalFlow::getDocumentId, documentId)
                .eq(ApprovalFlow::getStatus, SystemConstants.APPROVAL_STATUS_PENDING);
        ApprovalFlow existFlow = flowMapper.selectOne(existWrapper);
        if (existFlow != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "该文档已有待审批的申请");
        }

        ApprovalFlow flow = new ApprovalFlow();
        flow.setFlowId(IdUtil.fastSimpleUUID());
        flow.setDocumentId(documentId);
        flow.setSpaceId(spaceId);
        flow.setSecurityLevel(securityLevel);
        flow.setSubmitterId(submitterId);
        flow.setStatus(SystemConstants.APPROVAL_STATUS_PENDING);

        // 按安全等级决定审批步骤
        switch (securityLevel) {
            case SystemConstants.SECURITY_LEVEL_PUBLIC: // 公开 - 自动审批
                flow.setCurrentStep(1);
                flow.setTotalSteps(1);
                flow.setStatus(SystemConstants.APPROVAL_STATUS_APPROVED);
                flow.setResult("AUTO_APPROVED");
                log.info("文档[{}]安全等级为公开，自动审批通过", documentId);
                break;
            case SystemConstants.SECURITY_LEVEL_INTERNAL: // 内部 - 部门负责人审批
                flow.setCurrentStep(1);
                flow.setTotalSteps(1);
                createApprovalTask(flow.getFlowId(), 1, submitterId, "DEPT_HEAD");
                log.info("文档[{}]安全等级为内部，已创建部门负责人审批任务", documentId);
                break;
            case SystemConstants.SECURITY_LEVEL_SENSITIVE: // 敏感 - 部门负责人 + 合规部门双人审批
                flow.setCurrentStep(1);
                flow.setTotalSteps(2);
                createApprovalTask(flow.getFlowId(), 1, submitterId, "DEPT_HEAD");
                createApprovalTask(flow.getFlowId(), 2, null, "COMPLIANCE");
                log.info("文档[{}]安全等级为敏感，已创建双人审批任务", documentId);
                break;
            case SystemConstants.SECURITY_LEVEL_CONFIDENTIAL: // 机密 - 多级审批
                flow.setCurrentStep(1);
                flow.setTotalSteps(3);
                createApprovalTask(flow.getFlowId(), 1, submitterId, "DEPT_HEAD");
                createApprovalTask(flow.getFlowId(), 2, null, "COMPLIANCE");
                createApprovalTask(flow.getFlowId(), 3, null, "ADMIN");
                log.info("文档[{}]安全等级为机密，已创建多级审批任务", documentId);
                break;
            default:
                throw new BusinessException(ResultCode.PARAM_ERROR, "无效的安全等级");
        }

        flowMapper.insert(flow);
        return flow;
    }

    /**
     * 处理审批任务
     */
    @Transactional
    public ApprovalTask processApprovalTask(String taskId, String approverId, String status, String comment) {
        ApprovalTask task = taskMapper.selectOne(
                new LambdaQueryWrapper<ApprovalTask>()
                        .eq(ApprovalTask::getTaskId, taskId)
        );
        if (task == null) {
            throw new BusinessException(ResultCode.APPROVAL_NOT_FOUND);
        }
        if (!SystemConstants.APPROVAL_STATUS_PENDING.equals(task.getStatus())) {
            throw new BusinessException(ResultCode.APPROVAL_ALREADY_PROCESSED);
        }

        // 更新任务状态
        task.setApproverId(approverId);
        task.setStatus(status);
        task.setComment(comment);
        task.setApprovedAt(String.valueOf(System.currentTimeMillis()));
        taskMapper.updateById(task);

        // 获取审批流程
        ApprovalFlow flow = flowMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getFlowId, task.getFlowId())
        );

        // 如果拒绝，整个审批流程拒绝
        if (SystemConstants.APPROVAL_STATUS_REJECTED.equals(status)) {
            flow.setStatus(SystemConstants.APPROVAL_STATUS_REJECTED);
            flow.setResult("REJECTED_AT_STEP_" + task.getStep());
            flowMapper.updateById(flow);
            log.info("审批任务[{}]被拒绝，流程[{}]已终止", taskId, flow.getFlowId());

            // 发布审批拒绝事件，通知提交者
            eventPublisher.publishEvent(new ApprovalCompletedEvent(
                    this, flow.getFlowId(), flow.getDocumentId(),
                    flow.getSubmitterId(), "REJECTED", flow.getId()));

            return task;
        }

        // 审批通过，推进流程
        if (SystemConstants.APPROVAL_STATUS_APPROVED.equals(status)) {
            advanceApprovalFlow(flow, task.getStep());
        }

        return task;
    }

    /**
     * 推进审批流程
     */
    private void advanceApprovalFlow(ApprovalFlow flow, int currentStepCompleted) {
        if (currentStepCompleted < flow.getTotalSteps()) {
            // 还有后续步骤，推进到下一步
            flow.setCurrentStep(currentStepCompleted + 1);
            flowMapper.updateById(flow);
            log.info("流程[{}]推进到步骤 {}/{}", flow.getFlowId(), flow.getCurrentStep(), flow.getTotalSteps());
        } else {
            // 所有步骤完成，审批通过
            flow.setStatus(SystemConstants.APPROVAL_STATUS_APPROVED);
            flow.setResult("ALL_STEPS_APPROVED");
            flowMapper.updateById(flow);
            log.info("流程[{}]所有审批步骤完成，审批通过", flow.getFlowId());
            
            // 发布审批完成事件，异步处理后续流程
            eventPublisher.publishEvent(new ApprovalCompletedEvent(
                    this, flow.getFlowId(), flow.getDocumentId(), 
                    flow.getSubmitterId(), "APPROVED", flow.getId()));
        }
    }

    /**
     * 取消审批
     */
    @Transactional
    public boolean cancelApproval(String flowId, String operatorId) {
        ApprovalFlow flow = flowMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getFlowId, flowId)
        );
        if (flow == null) {
            throw new BusinessException(ResultCode.APPROVAL_NOT_FOUND, "审批流程不存在");
        }
        
        // 只有提交者可以取消
        if (!flow.getSubmitterId().equals(operatorId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有提交者可以取消审批");
        }
        
        // 只有待审批状态可以取消
        if (!SystemConstants.APPROVAL_STATUS_PENDING.equals(flow.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前状态不允许取消");
        }

        flow.setStatus(SystemConstants.APPROVAL_STATUS_CANCELLED);
        flow.setResult("CANCELLED_BY_SUBMITTER");
        flowMapper.updateById(flow);
        
        // 取消所有待处理的任务
        List<ApprovalTask> pendingTasks = taskMapper.selectList(
                new LambdaQueryWrapper<ApprovalTask>()
                        .eq(ApprovalTask::getFlowId, flowId)
                        .eq(ApprovalTask::getStatus, SystemConstants.APPROVAL_STATUS_PENDING)
        );
        for (ApprovalTask task : pendingTasks) {
            task.setStatus(SystemConstants.APPROVAL_STATUS_CANCELLED);
            task.setComment("审批流程已取消");
            taskMapper.updateById(task);
        }
        
        log.info("审批流程[{}]已被取消，取消了{}个待处理任务", flowId, pendingTasks.size());
        return true;
    }

    /**
     * 获取审批详情
     */
    public ApprovalFlowDetailResponse getApprovalDetail(String flowId) {
        ApprovalFlow flow = flowMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getFlowId, flowId)
        );
        if (flow == null) {
            throw new BusinessException(ResultCode.APPROVAL_NOT_FOUND, "审批流程不存在");
        }

        // 获取所有任务
        List<ApprovalTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<ApprovalTask>()
                        .eq(ApprovalTask::getFlowId, flowId)
                        .orderByAsc(ApprovalTask::getStep)
        );

        ApprovalFlowDetailResponse response = new ApprovalFlowDetailResponse();
        response.setFlowId(flow.getFlowId());
        response.setDocumentId(flow.getDocumentId());
        response.setSpaceId(flow.getSpaceId());
        response.setSecurityLevel(flow.getSecurityLevel());
        response.setSubmitterId(flow.getSubmitterId());
        response.setCurrentStep(flow.getCurrentStep());
        response.setTotalSteps(flow.getTotalSteps());
        response.setStatus(flow.getStatus());
        response.setResult(flow.getResult());
        response.setCreatedAt(flow.getCreatedAt());
        response.setUpdatedAt(flow.getUpdatedAt());

        List<ApprovalFlowDetailResponse.TaskDetail> taskDetails = tasks.stream()
                .map(ApprovalFlowDetailResponse.TaskDetail::fromEntity)
                .collect(Collectors.toList());
        response.setTasks(taskDetails);

        return response;
    }

    /**
     * 分页查询审批流程
     */
    public PageResult<ApprovalFlow> pageApprovalFlows(PageRequest pageRequest, String status, 
                                                       String submitterId, Integer securityLevel) {
        Page<ApprovalFlow> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<ApprovalFlow> wrapper = new LambdaQueryWrapper<>();
        
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ApprovalFlow::getStatus, status);
        }
        if (submitterId != null && !submitterId.isEmpty()) {
            wrapper.eq(ApprovalFlow::getSubmitterId, submitterId);
        }
        if (securityLevel != null) {
            wrapper.eq(ApprovalFlow::getSecurityLevel, securityLevel);
        }
        
        wrapper.orderByDesc(ApprovalFlow::getCreatedAt);
        Page<ApprovalFlow> result = flowMapper.selectPage(page, wrapper);
        
        return PageResult.of(result.getRecords(), result.getTotal(), 
                            pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 分页查询我的待办任务
     */
    public PageResult<ApprovalTask> pageMyPendingTasks(PageRequest pageRequest, String approverId, 
                                                        String approverType) {
        Page<ApprovalTask> page = new Page<>(pageRequest.getPageNum(), pageRequest.getPageSize());
        LambdaQueryWrapper<ApprovalTask> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(ApprovalTask::getStatus, SystemConstants.APPROVAL_STATUS_PENDING);
        
        // 按审批人ID或审批人类型筛选
        if (approverId != null && !approverId.isEmpty()) {
            wrapper.and(w -> w.eq(ApprovalTask::getApproverId, approverId)
                    .or()
                    .isNull(ApprovalTask::getApproverId));
        }
        if (approverType != null && !approverType.isEmpty()) {
            wrapper.eq(ApprovalTask::getApproverType, approverType);
        }
        
        wrapper.orderByAsc(ApprovalTask::getCreatedAt);
        Page<ApprovalTask> result = taskMapper.selectPage(page, wrapper);
        
        return PageResult.of(result.getRecords(), result.getTotal(),
                            pageRequest.getPageNum(), pageRequest.getPageSize());
    }

    /**
     * 根据文档ID获取最新审批状态
     */
    public ApprovalFlow getLatestApprovalByDocument(String documentId) {
        return flowMapper.selectOne(
                new LambdaQueryWrapper<ApprovalFlow>()
                        .eq(ApprovalFlow::getDocumentId, documentId)
                        .orderByDesc(ApprovalFlow::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    /**
     * 统计待审批数量
     */
    public long countPendingApprovals(String approverType) {
        return taskMapper.selectCount(
                new LambdaQueryWrapper<ApprovalTask>()
                        .eq(ApprovalTask::getStatus, SystemConstants.APPROVAL_STATUS_PENDING)
                        .eq(approverType != null, ApprovalTask::getApproverType, approverType)
        );
    }

    /**
     * 创建审批任务
     */
    private void createApprovalTask(String flowId, int step, String approverId, String approverType) {
        ApprovalTask task = new ApprovalTask();
        task.setTaskId(IdUtil.fastSimpleUUID());
        task.setFlowId(flowId);
        task.setStep(step);
        task.setApproverId(approverId);
        task.setApproverType(approverType);
        task.setStatus(SystemConstants.APPROVAL_STATUS_PENDING);
        taskMapper.insert(task);
    }
}
