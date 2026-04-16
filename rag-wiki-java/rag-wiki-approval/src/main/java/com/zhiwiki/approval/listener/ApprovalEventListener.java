package com.zhiwiki.approval.listener;

import com.zhiwiki.approval.event.ApprovalCompletedEvent;
import com.zhiwiki.approval.service.ApprovalService;
import com.zhiwiki.common.model.Result;
import com.zhiwiki.common.notification.NotificationService;
import com.zhiwiki.document.feign.AiServiceClient;
import com.zhiwiki.document.feign.DocumentServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 审批事件监听器
 * 处理审批完成后的后续流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalEventListener {

    private final ApprovalService approvalService;
    private final DocumentServiceClient documentServiceClient;
    private final AiServiceClient aiServiceClient;
    private final NotificationService notificationService;

    /**
     * 异步处理审批完成事件
     */
    @Async("taskExecutor")
    @EventListener
    public void handleApprovalCompleted(ApprovalCompletedEvent event) {
        log.info("收到审批完成事件: flowId={}, result={}", event.getFlowId(), event.getResult());
        
        try {
            if ("APPROVED".equals(event.getResult())) {
                // 审批通过，触发文档解析入库
                handleApproved(event);
            } else {
                // 审批拒绝
                handleRejected(event);
            }
            
            // 发送通知给提交者
            sendNotification(event);
            
        } catch (Exception e) {
            log.error("处理审批完成事件失败: flowId={}", event.getFlowId(), e);
        }
    }

    /**
     * 处理审批通过
     */
    private void handleApproved(ApprovalCompletedEvent event) {
        log.info("处理审批通过后续流程: documentId={}", event.getDocumentId());
        
        // 1. 触发文档解析任务（调用Python服务）
        triggerDocumentParsing(event.getDocumentId());
        
        // 2. 更新文档状态为已批准
        updateDocumentStatus(event.getDocumentId(), "APPROVED");
        
        // 3. 记录操作日志
        log.info("文档[{}]审批通过，已触发解析流程", event.getDocumentId());
    }

    /**
     * 处理审批拒绝
     */
    private void handleRejected(ApprovalCompletedEvent event) {
        log.info("处理审批拒绝: documentId={}", event.getDocumentId());
        
        // 1. 更新文档状态为已拒绝
        updateDocumentStatus(event.getDocumentId(), "REJECTED");
        
        // 2. 记录拒绝原因（如果有）
        log.info("文档[{}]审批被拒绝", event.getDocumentId());
    }

    /**
     * 触发文档解析
     */
    private void triggerDocumentParsing(String documentId) {
        try {
            // 1. 获取文档详情
            Result<Map<String, Object>> docResult = documentServiceClient.getDocument(documentId);
            if (docResult.getCode() != 200 || docResult.getData() == null) {
                log.error("获取文档详情失败: documentId={}", documentId);
                return;
            }
            
            Map<String, Object> document = docResult.getData();
            String fileUrl = (String) document.get("fileUrl");
            String spaceId = (String) document.get("spaceId");
            Integer securityLevel = (Integer) document.getOrDefault("securityLevel", 1);
            
            // 2. 调用Python AI服务提交解析任务
            Map<String, Object> parseRequest = new HashMap<>();
            parseRequest.put("document_id", documentId);
            parseRequest.put("file_url", fileUrl);
            parseRequest.put("space_id", spaceId);
            parseRequest.put("security_level", securityLevel);
            parseRequest.put("parser_type", "auto");
            parseRequest.put("need_ocr", true);
            
            Result<Map<String, Object>> parseResult = aiServiceClient.submitParseTask(parseRequest);
            if (parseResult.getCode() == 200 && parseResult.getData() != null) {
                String taskId = (String) parseResult.getData().get("task_id");
                log.info("文档解析任务已提交: documentId={}, taskId={}", documentId, taskId);
                
                // 3. 更新文档解析状态
                documentServiceClient.updateParseStatus(documentId, "PARSING", taskId);
            } else {
                log.error("提交解析任务失败: documentId={}, msg={}", documentId, parseResult.getMessage());
            }
            
        } catch (Exception e) {
            log.error("触发文档解析失败: documentId={}", documentId, e);
        }
    }

    /**
     * 更新文档状态
     */
    private void updateDocumentStatus(String documentId, String status) {
        try {
            Result<Boolean> result = documentServiceClient.updateStatus(documentId, status);
            if (result.getCode() == 200 && Boolean.TRUE.equals(result.getData())) {
                log.info("文档状态更新成功: documentId={}, status={}", documentId, status);
            } else {
                log.error("文档状态更新失败: documentId={}, status={}", documentId, status);
            }
        } catch (Exception e) {
            log.error("更新文档状态失败: documentId={}", documentId, e);
        }
    }

    /**
     * 发送通知
     */
    private void sendNotification(ApprovalCompletedEvent event) {
        try {
            boolean isApproved = "APPROVED".equals(event.getResult());
            
            // 使用NotificationService发送多渠道审批通知
            notificationService.sendApprovalNotification(
                event.getSubmitterId(), 
                isApproved, 
                event.getDocumentId(),
                event.getDocumentTitle() != null ? event.getDocumentTitle() : event.getDocumentId()
            );
            
            // 审批拒绝时额外发送邮件提醒
            if (!isApproved) {
                notificationService.sendEmail(
                    event.getSubmitterId() + "@company.com",
                    "文档审批未通过",
                    String.format("您的文档《%s》审批未通过，请查看审批意见并修改后重新提交。",
                        event.getDocumentTitle() != null ? event.getDocumentTitle() : event.getDocumentId())
                );
            }
            
            log.info("审批通知已发送: userId={}, approved={}", event.getSubmitterId(), isApproved);
            
        } catch (Exception e) {
            log.error("发送通知失败: userId={}", event.getSubmitterId(), e);
        }
    }
}