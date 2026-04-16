package com.zhiwiki.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建Badcase请求DTO
 */
@Data
@Schema(description = "创建Badcase请求")
public class BadcaseCreateRequest {

    @Schema(description = "问题描述", required = true)
    @NotBlank(message = "问题描述不能为空")
    @Size(min = 5, max = 2000, message = "问题描述长度必须在5-2000之间")
    private String description;

    @Schema(description = "原始问题")
    @Size(max = 1000, message = "原始问题不能超过1000字")
    private String originalQuery;

    @Schema(description = "AI回答内容")
    @Size(max = 5000, message = "AI回答不能超过5000字")
    private String aiAnswer;

    @Schema(description = "严重程度: CRITICAL/HIGH/MEDIUM/LOW", required = true)
    @NotBlank(message = "严重程度不能为空")
    @Pattern(regexp = "^(CRITICAL|HIGH|MEDIUM|LOW)$", message = "严重程度只能是CRITICAL/HIGH/MEDIUM/LOW")
    private String severity;

    @Schema(description = "Badcase类型: RETRIEVAL_FAILURE/HALLUCINATION/ROUTING_ERROR/KNOWLEDGE_GAP", required = true)
    @NotBlank(message = "Badcase类型不能为空")
    @Pattern(regexp = "^(RETRIEVAL_FAILURE|HALLUCINATION|ROUTING_ERROR|KNOWLEDGE_GAP)$",
             message = "Badcase类型不合法")
    private String caseType;

    @Schema(description = "来源: USER_FEEDBACK/CUSTOMER_TICKET/AUTO_DETECTION")
    @Pattern(regexp = "^(USER_FEEDBACK|CUSTOMER_TICKET|AUTO_DETECTION)$",
             message = "来源类型不合法")
    private String source;

    @Schema(description = "关联的会话ID")
    @Size(max = 64, message = "会话ID不能超过64字")
    private String sessionId;

    @Schema(description = "关联的知识库空间ID")
    @Size(max = 64, message = "空间ID不能超过64字")
    private String spaceId;

    @Schema(description = "补充说明")
    @Size(max = 1000, message = "补充说明不能超过1000字")
    private String additionalInfo;
}
