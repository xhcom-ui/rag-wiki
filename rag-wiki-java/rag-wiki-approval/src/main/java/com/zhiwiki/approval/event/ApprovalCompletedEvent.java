package com.zhiwiki.approval.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 审批完成事件
 */
@Getter
public class ApprovalCompletedEvent extends ApplicationEvent {

    private final String flowId;
    private final String documentId;
    private final String documentTitle;
    private final String submitterId;
    private final String result; // APPROVED / REJECTED
    private final Long approvalId;

    public ApprovalCompletedEvent(Object source, String flowId, String documentId,
                                   String submitterId, String result, Long approvalId) {
        super(source);
        this.flowId = flowId;
        this.documentId = documentId;
        this.documentTitle = null;
        this.submitterId = submitterId;
        this.result = result;
        this.approvalId = approvalId;
    }

    public ApprovalCompletedEvent(Object source, String flowId, String documentId,
                                   String documentTitle, String submitterId,
                                   String result, Long approvalId) {
        super(source);
        this.flowId = flowId;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.submitterId = submitterId;
        this.result = result;
        this.approvalId = approvalId;
    }
}