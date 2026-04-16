package com.zhiwiki.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全告警响应
 */
@Data
@Schema(description = "安全告警")
public class SecurityAlertResponse {

    @Schema(description = "告警ID")
    private String alertId;

    @Schema(description = "告警类型")
    private AlertType alertType;

    @Schema(description = "告警类型名称")
    private String alertTypeName;

    @Schema(description = "告警级别")
    private AlertLevel alertLevel;

    @Schema(description = "告警级别名称")
    private String alertLevelName;

    @Schema(description = "告警标题")
    private String title;

    @Schema(description = "告警详情")
    private String description;

    @Schema(description = "相关用户ID")
    private String userId;

    @Schema(description = "相关用户名")
    private String username;

    @Schema(description = "相关IP地址")
    private String clientIp;

    @Schema(description = "告警状态")
    private AlertStatus status;

    @Schema(description = "处理人")
    private String handledBy;

    @Schema(description = "处理时间")
    private LocalDateTime handledAt;

    @Schema(description = "处理备注")
    private String handleRemark;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /**
     * 告警类型枚举
     */
    public enum AlertType {
        HIGH_FREQUENCY_QUERY,       // 高频查询
        CROSS_DEPT_ACCESS,          // 跨部门访问
        HIGH_LEVEL_DOC_ACCESS,      // 高密级文档访问
        ABNORMAL_TIME_ACCESS,       // 非工作时间访问
        PERMISSION_VIOLATION,       // 权限违规
        BATCH_DOWNLOAD,             // 批量下载
        FAILED_LOGIN_ATTEMPT,       // 登录失败尝试
        TOKEN_ANOMALY,              // Token异常
        DATA_EXPORT,                // 数据导出
        CUSTOM                      // 自定义
    }

    /**
     * 告警级别枚举
     */
    public enum AlertLevel {
        INFO,       // 信息
        WARNING,    // 警告
        ERROR,      // 错误
        CRITICAL    // 严重
    }

    /**
     * 告警状态枚举
     */
    public enum AlertStatus {
        NEW,        // 新告警
        HANDLING,   // 处理中
        HANDLED,    // 已处理
        IGNORED     // 已忽略
    }

    public String getAlertTypeName() {
        if (alertType == null) return "";
        switch (alertType) {
            case HIGH_FREQUENCY_QUERY: return "高频查询";
            case CROSS_DEPT_ACCESS: return "跨部门访问";
            case HIGH_LEVEL_DOC_ACCESS: return "高密级文档访问";
            case ABNORMAL_TIME_ACCESS: return "非工作时间访问";
            case PERMISSION_VIOLATION: return "权限违规";
            case BATCH_DOWNLOAD: return "批量下载";
            case FAILED_LOGIN_ATTEMPT: return "登录失败尝试";
            case TOKEN_ANOMALY: return "Token异常";
            case DATA_EXPORT: return "数据导出";
            case CUSTOM: return "自定义";
            default: return alertType.name();
        }
    }

    public String getAlertLevelName() {
        if (alertLevel == null) return "";
        switch (alertLevel) {
            case INFO: return "信息";
            case WARNING: return "警告";
            case ERROR: return "错误";
            case CRITICAL: return "严重";
            default: return alertLevel.name();
        }
    }
}
